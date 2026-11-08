package org.praskanth.config;

import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executors;

/**
 * Makes the Feign circuit breaker run its calls on threads that know who the caller is (see
 * ContextPropagatingExecutorService). Needs  spring.cloud.circuitbreaker.bulkhead.resilience4j.enabled=false  in
 * ORDER-SERVICE.yml: otherwise Spring Cloud runs the calls in its own fixed thread pool and ignores this executor.
 */
@Configuration
public class FeignCircuitBreakerContextConfig {

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> contextPropagatingExecutor() {
        return factory -> factory.configureExecutorService(
                new ContextPropagatingExecutorService(Executors.newCachedThreadPool()));
    }
}
