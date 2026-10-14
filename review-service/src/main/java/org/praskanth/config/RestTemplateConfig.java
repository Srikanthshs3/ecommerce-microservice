package org.praskanth.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Load-balanced (Eureka) client for calls to product-, order- and user-service. common-library automatically adds
 * the caller's X-User-* identity headers to every @LoadBalanced RestTemplate, so no token handling is needed here.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
