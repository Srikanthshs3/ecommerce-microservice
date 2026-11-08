package org.praskanth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Binds to `security.public-paths` in application.yml/config-repo so open endpoints can be
 * changed per-environment without touching code or redeploying.
 *
 * Entry syntax:  "[METHOD ]ant-pattern"
 *   "/api/v1/auth/**"          any method
 *   "GET /api/v1/product/**"   only GET (writes such as POST /api/v1/product/create still need a token)
 *
 * On a public path a valid token is still honoured (identity headers are forwarded) so endpoints like
 * /api/v1/auth/me work; a missing or invalid token simply means "anonymous".
 *
 * IMPORTANT: if the external config-repo defines `security.public-paths` for the gateway it REPLACES this
 * list (Spring does not merge lists) - update it there too.
 */
@Configuration
@ConfigurationProperties(prefix = "security")
@Data
public class ApiGatewayProperties {

    private List<String> publicPaths = List.of(
            "/api/v1/auth/**",
            "GET /api/v1/product/**",
            "GET /api/v1/category/**",
            "POST /api/v1/sellers/register",
            "POST /api/v1/delivery-agents/register",
            "GET /api/v1/sellers/*/public",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/health/**",
            "/actuator/prometheus",
            "/fallback"
    );
}
