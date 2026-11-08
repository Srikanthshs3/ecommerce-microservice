package org.praskanth.resilience;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * A plain {@code new RestTemplate()} has NO timeouts: one hung service can pin every Tomcat thread of its callers
 * forever. This gives every RestTemplate bean a connect and a read timeout, which is the real "time limiter" for
 * blocking calls (Resilience4j's TimeLimiter only works on async calls).
 *
 * Tune per environment in the config repo:
 *   resilience.http.connect-timeout: 2s      resilience.http.read-timeout: 6s      resilience.http.enabled: true
 */
@Configuration
@ConditionalOnClass(RestTemplate.class)
public class HttpTimeoutConfiguration {

    // static: BeanPostProcessors must be created before the rest of the configuration
    @Bean
    static BeanPostProcessor restTemplateTimeoutPostProcessor(Environment env) {
        boolean enabled = env.getProperty("resilience.http.enabled", Boolean.class, true);
        Duration connect = env.getProperty("resilience.http.connect-timeout", Duration.class, Duration.ofSeconds(2));
        Duration read = env.getProperty("resilience.http.read-timeout", Duration.class, Duration.ofSeconds(6));
        return new BeanPostProcessor() {
            private final Logger log = LoggerFactory.getLogger(HttpTimeoutConfiguration.class);

            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                if (enabled && bean instanceof RestTemplate restTemplate) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout((int) connect.toMillis());
                    factory.setReadTimeout((int) read.toMillis());
                    restTemplate.setRequestFactory(factory); // interceptors (load balancer, identity) still wrap it
                    log.info("RestTemplate '{}': connect timeout {}, read timeout {}", beanName, connect, read);
                }
                return bean;
            }
        };
    }
}
