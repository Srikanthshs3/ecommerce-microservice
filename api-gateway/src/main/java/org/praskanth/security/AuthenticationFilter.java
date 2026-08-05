package org.praskanth.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.praskanth.config.ApiGatewayProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
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
import java.util.UUID;

/**
 * Runs before every routed request. Public paths (auth, docs, actuator health) pass straight
 * through; everything else must carry a valid "Bearer <jwt>" issued by user-service.
 *
 * On success, forwards the resolved identity downstream as X-Auth-User / X-Auth-Roles so
 * services don't need to re-parse or even see the raw JWT, and stamps a correlation id used
 * for cross-service log tracing (picked up by CorrelationIdFilter in common-library).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuthenticationFilter implements GlobalFilter, Ordered {

    private static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    private static final String AUTH_USER_HEADER = "X-Auth-User";
    private static final String AUTH_ROLES_HEADER = "X-Auth-Roles";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;
    private final ApiGatewayProperties apiGatewayProperties;
    private final ObjectMapper objectMapper; // Use injected ObjectMapper
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public int getOrder() {
        // Runs immediately after standard routing but before specific routing filters
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 1. Establish Correlation ID
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        // 2. Prepare Request Mutator
        // IMPORTANT: Strip any existing X-Auth-* headers to prevent spoofing from external clients
        ServerHttpRequest.Builder requestBuilder = request.mutate()
                .headers(headers -> {
                    headers.remove(AUTH_USER_HEADER);
                    headers.remove(AUTH_ROLES_HEADER);
                })
                .header(CORRELATION_ID_HEADER, correlationId);

        // 3. Handle Public Paths
        if (isPublicPath(path)) {
            log.debug("Permitting public path: {}", path);
            return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
        }

        // 4. Extract and Validate Token for Protected Paths
        String authHeader = request.getHeaders().getFirst(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            return onError(exchange, "MISSING_TOKEN", "Missing or malformed Authorization header", HttpStatus.UNAUTHORIZED);
        }

        String token = authHeader.substring(BEARER_PREFIX.length()).trim();

        if (!jwtUtils.isTokenValid(token)) {
            return onError(exchange, "INVALID_TOKEN", "Invalid or expired token", HttpStatus.UNAUTHORIZED);
        }

        // 5. Apply Identity Context
        String username = jwtUtils.getUsernameFromToken(token);
        String roles = String.join(",", jwtUtils.getRolesFromToken(token));

        log.info("Authenticated request path={} user={} correlationId={}", path, username, correlationId);

        requestBuilder
                .header(AUTH_USER_HEADER, username)
                .header(AUTH_ROLES_HEADER, roles);

        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    private boolean isPublicPath(String path) {
        return apiGatewayProperties.getPublicPaths().stream()
                .anyMatch(publicPath -> pathMatcher.match(publicPath, path));
    }

    private Mono<Void> onError(ServerWebExchange exchange, String code, String message, HttpStatus status) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String requestPath = exchange.getRequest().getURI().getPath();
        log.warn("Auth rejected [{}]: {} for path {}", code, message, requestPath);

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
            log.error("Failed to serialize auth error response", e);
            bytes = String.format("{\"success\":false,\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}", code, message)
                    .getBytes(StandardCharsets.UTF_8);
        }

        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        // Write the response and explicitly release the buffer to prevent Netty memory leaks
        return response.writeWith(Mono.just(buffer))
                .doOnSuccess(aVoid -> DataBufferUtils.release(buffer))
                .doOnError(throwable -> DataBufferUtils.release(buffer));
    }
}
