package org.praskanth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Binds to `security.public-paths` in application.yml/config-repo so open endpoints can be
 * changed per-environment without touching code or redeploying.
 */
@Configuration
@ConfigurationProperties(prefix = "security")
@Data
public class ApiGatewayProperties {

    private List<String> publicPaths = List.of(
            "/api/v1/auth/",
            "/swagger-ui",
            "/v3/api-docs",
            "/actuator/health",
            "/actuator/prometheus",
            "/fallback"
    );
}
