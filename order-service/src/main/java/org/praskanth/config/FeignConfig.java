package org.praskanth.config;

import feign.Logger;
import feign.RequestInterceptor;
import feign.Retryer;
import org.praskanth.security.gateway.IdentityPropagation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL; // overridden by loggerLevel: basic in ORDER-SERVICE.yml (FULL logs headers and bodies)
    }

    /**
     * Retry on connection problems and timeouts only (Feign throws RetryableException for those), 3 attempts, waiting
     * 200 ms then 400 ms. Safe here because every Feign call of order-service is repeatable: reading / clearing the
     * cart, and coupon redeem (idempotent per checkoutRef) and release ("safe to repeat").
     * A call that is NOT repeatable must not be added to a Feign client without revisiting this.
     */
    @Bean
    Retryer feignRetryer() {
        return new Retryer.Default(200, 800, 3);
    }

    /** Forward the gateway-verified identity (X-User-*) to the called service. Feign is internal-only (Eureka). */
    @Bean
    RequestInterceptor identityPropagationInterceptor() {
        return template -> IdentityPropagation.currentHeaders().forEach((name, value) -> template.header(name, value));
    }
}
