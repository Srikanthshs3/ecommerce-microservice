package org.praskanth.config;

import feign.Logger;
import feign.RequestInterceptor;
import org.praskanth.security.gateway.IdentityPropagation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

    /** Forward the gateway-verified identity (X-User-*) to the called service. Feign is internal-only (Eureka). */
    @Bean
    RequestInterceptor identityPropagationInterceptor() {
        return template -> IdentityPropagation.currentHeaders().forEach((name, value) -> template.header(name, value));
    }
}
