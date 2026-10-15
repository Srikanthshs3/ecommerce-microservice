package org.praskanth.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.config.ApiGatewayProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * THE trust boundary of the platform.
 *
 * For every request this filter
 *   1. strips every identity header a client might have sent (X-User-*, X-Gateway-*, legacy X-Auth-*),
 *   2. verifies the JWT once (see {@link JwtUtils}),
 *   3. forwards the verified identity as X-User-Id / X-User-Email / X-User-Roles
 *      (+ X-Gateway-Timestamp / X-Gateway-Signature when gateway.identity.signing-secret is configured),
 *   4. removes the Authorization header, so downstream services never see (or log, or re-forward) the raw token.
 *
 * Public paths pass without a token, but a valid token is still honoured. Any path under /internal/ is never
 * routable from outside: it exists for service-to-service calls only.
 *
 * This filter only AUTHENTICATES. Authorisation (roles, ownership) is enforced by each service with the
 * forwarded identity.
 */
@Component
@Slf4j
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String INTERNAL_PREFIX = "/internal/";

    private final JwtUtils jwtUtils;
    private final ApiGatewayProperties apiGatewayProperties;
    private final ObjectMapper objectMapper;
    private final HeaderSigner signer; // null => headers are forwarded unsigned
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuthenticationFilter(JwtUtils jwtUtils,
                                ApiGatewayProperties apiGatewayProperties,
                                ObjectMapper objectMapper,
                                @Value("${gateway.identity.signing-secret:}") String signingSecret) {
        this.jwtUtils = jwtUtils;
        this.apiGatewayProperties = apiGatewayProperties;
        this.objectMapper = objectMapper;
        if (StringUtils.hasText(signingSecret)) {
            this.signer = new HeaderSigner(signingSecret);
            log.info("Gateway identity headers will be HMAC-signed");
        } else {
            this.signer = null;
            log.warn("gateway.identity.signing-secret is NOT set: identity headers are forwarded UNSIGNED. "
                    + "That is only safe if downstream services are unreachable except through this gateway.");
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // Never reachable from outside, whatever the route configuration says.
        if (path.startsWith(INTERNAL_PREFIX)) {
            return onError(exchange, "NOT_FOUND", "Resource not found", HttpStatus.NOT_FOUND);
        }
        // Public-path matching works on the decoded path; refuse dot-segments so "/api/v1/auth/../user/x" can't be
        // used to make a protected resource look like a public one.
        if (hasDotSegment(path)) {
            return onError(exchange, "BAD_REQUEST", "Malformed request path", HttpStatus.BAD_REQUEST);
        }

        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        // Always start from a clean slate: whatever identity headers the client sent are discarded.
        ServerHttpRequest.Builder forwarded = request.mutate()
                .headers(h -> {
                    h.remove(GatewayHeaders.USER_ID);
                    h.remove(GatewayHeaders.USER_EMAIL);
                    h.remove(GatewayHeaders.USER_ROLES);
                    h.remove(GatewayHeaders.TIMESTAMP);
                    h.remove(GatewayHeaders.SIGNATURE);
                    h.remove(GatewayHeaders.LEGACY_AUTH_USER);
                    h.remove(GatewayHeaders.LEGACY_AUTH_ROLES);
                    h.remove(HttpHeaders.AUTHORIZATION); // the raw token stops here
                })
                .header(CORRELATION_ID_HEADER, correlationId);

        // CORS preflight carries no credentials; the CORS web filter normally answers it before we get here.
        if (HttpMethod.OPTIONS.equals(request.getMethod())) {
            return proceed(exchange, chain, forwarded);
        }

        boolean isPublic = isPublicPath(request.getMethod(), path);
        String token = extractBearerToken(request);

        if (token == null) {
            return isPublic
                    ? proceed(exchange, chain, forwarded)
                    : onError(exchange, "MISSING_TOKEN", "Missing or malformed Authorization header", HttpStatus.UNAUTHORIZED);
        }

        Optional<JwtUtils.Identity> identity = jwtUtils.authenticate(token);
        if (identity.isEmpty()) {
            return isPublic
                    ? proceed(exchange, chain, forwarded) // bad token on an open endpoint == anonymous
                    : onError(exchange, "INVALID_TOKEN", "Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }

        JwtUtils.Identity id = identity.get();
        String userId = String.valueOf(id.userId());
        String roles = String.join(",", id.roles());
        long now = Instant.now().getEpochSecond();

        forwarded.headers(h -> {
            h.set(GatewayHeaders.USER_ID, userId);
            h.set(GatewayHeaders.USER_EMAIL, id.email());
            h.set(GatewayHeaders.USER_ROLES, roles);
            if (signer != null) {
                h.set(GatewayHeaders.TIMESTAMP, String.valueOf(now));
                h.set(GatewayHeaders.SIGNATURE, signer.sign(userId, id.email(), roles, now));
            }
        });

        log.debug("Authenticated path={} userId={} correlationId={}", path, userId, correlationId);
        return proceed(exchange, chain, forwarded);
    }

    private Mono<Void> proceed(ServerWebExchange exchange, GatewayFilterChain chain, ServerHttpRequest.Builder forwarded) {
        return chain.filter(exchange.mutate().request(forwarded.build()).build());
    }

    private static String extractBearerToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = authHeader.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }

    private static boolean hasDotSegment(String path) {
        for (String segment : path.split("/")) {
            if (segment.equals("..") || segment.equals(".")) {
                return true;
            }
        }
        return false;
    }

    /** Entries are "[METHOD ]ant-pattern", e.g. "GET /api/v1/product/**". */
    private boolean isPublicPath(HttpMethod method, String path) {
        for (String entry : apiGatewayProperties.getPublicPaths()) {
            String pattern = entry.trim();
            int space = pattern.indexOf(' ');
            if (space > 0) {
                String allowedMethod = pattern.substring(0, space);
                if (method == null || !allowedMethod.equalsIgnoreCase(method.name())) {
                    continue;
                }
                pattern = pattern.substring(space + 1).trim();
            }
            if (pathMatcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    private Mono<Void> onError(ServerWebExchange exchange, String code, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status == HttpStatus.UNAUTHORIZED) {
            response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }

        String requestPath = exchange.getRequest().getURI().getPath();
        log.warn("Request rejected [{}]: {} for path {}", code, message, requestPath);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);

        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("path", requestPath);
        error.put("timestamp", Instant.now().toString());
        body.put("error", error);

        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize error response", e);
            bytes = String.format("{\"success\":false,\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}", code, message)
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }
}
