package org.praskanth.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Spring Cloud Gateway's RequestRateLimiter answers "too many requests" with status 429 and an EMPTY body, which the
 * browser shows as "Request failed (429)". This filter wraps the response: when something completes it as a body-less
 * 429, it writes the platform envelope with a plain sentence and a Retry-After header instead.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RateLimitResponseFilter implements WebFilter {

    private final ObjectMapper objectMapper;

    public RateLimitResponseFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpResponse decorated = new ServerHttpResponseDecorator(exchange.getResponse()) {
            @Override
            public Mono<Void> setComplete() {
                boolean tooMany = getStatusCode() != null && getStatusCode().value() == 429;
                if (tooMany && !isCommitted() && getHeaders().getContentType() == null) {
                    byte[] bytes = body(GatewayErrors.originalPath(exchange), GatewayErrors.area(exchange));
                    getHeaders().setContentType(MediaType.APPLICATION_JSON);
                    getHeaders().setContentLength(bytes.length);
                    getHeaders().set(HttpHeaders.RETRY_AFTER, "2");
                    return writeWith(Mono.just(bufferFactory().wrap(bytes)));
                }
                return super.setComplete();
            }
        };
        return chain.filter(exchange.mutate().response(decorated).build());
    }

    private byte[] body(String path, String area) {
        try {
            return objectMapper.writeValueAsBytes(
                    GatewayErrors.body("TOO_MANY_REQUESTS", GatewayErrors.forStatus(429, area), path, null));
        } catch (JsonProcessingException e) {
            return ("{\"success\":false,\"error\":{\"code\":\"TOO_MANY_REQUESTS\",\"message\":\""
                    + GatewayErrors.forStatus(429, null) + "\"}}").getBytes(StandardCharsets.UTF_8);
        }
    }
}
