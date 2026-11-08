package org.praskanth.config;

import org.praskanth.security.GatewayHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;

/**
 * "Who is this caller?" for the Redis rate limiter (see the RequestRateLimiter filters in API-GATEWAY.yml).
 *
 *   logged in   ->  the user  (X-User-Id is set by AuthenticationFilter only after the JWT was verified, and any value the
 *                   client sent itself was removed first, so it cannot be spoofed)
 *   anonymous   ->  the client IP
 *
 * The ROUTE ID is part of the key, so every route has its own bucket: hammering /product cannot use up the login budget.
 *
 * Behind a trusted reverse proxy / load balancer set  gateway.rate-limit.trust-forwarded-for=true  so the IP comes from
 * X-Forwarded-For. Leave it false when clients reach the gateway directly, otherwise a client could invent addresses.
 */
@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver routeUserOrIpKeyResolver(@Value("${gateway.rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor) {
        return exchange -> {
            Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            String routeId = route == null ? "no-route" : route.getId();
            String userId = exchange.getRequest().getHeaders().getFirst(GatewayHeaders.USER_ID);
            String caller = StringUtils.hasText(userId) ? "u:" + userId.trim() : "ip:" + clientIp(exchange, trustForwardedFor);
            return Mono.just(routeId + ":" + caller);
        };
    }

    static String clientIp(ServerWebExchange exchange, boolean trustForwardedFor) {
        if (trustForwardedFor) {
            String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return forwarded.split(",")[0].trim(); // the client; later entries are proxies
            }
        }
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        return remote == null || remote.getAddress() == null ? "unknown" : remote.getAddress().getHostAddress();
    }
}
