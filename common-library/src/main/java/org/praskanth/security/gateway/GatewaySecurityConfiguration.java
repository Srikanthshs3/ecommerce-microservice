package org.praskanth.security.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Shared "trust the gateway" wiring. Picked up by the normal component scan (every service's application class
 * lives in org.praskanth, same as CorrelationIdFilter / GlobalExceptionHandler) and only active in servlet
 * services that have Spring Security on the classpath.
 *
 * Each service then needs only a SecurityFilterChain that references the beans below - see e.g. cart-service.
 *
 * Properties (all optional):
 *   gateway.identity.signing-secret     same value as the gateway's; when set, unsigned/forged headers are rejected
 *   gateway.identity.max-skew-seconds   accepted age of a signature (default 60)
 */
@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.security.web.SecurityFilterChain")
public class GatewaySecurityConfiguration {

    private static final Logger log = LoggerFactory.getLogger(GatewaySecurityConfiguration.class);

    @Bean
    public GatewayAuthenticationFilter gatewayAuthenticationFilter(
            @Value("${gateway.identity.signing-secret:${GATEWAY_IDENTITY_SIGNING_SECRET:}}") String signingSecret,
            @Value("${gateway.identity.max-skew-seconds:60}") long maxSkewSeconds) {

        HeaderSigner signer = null;
        if (StringUtils.hasText(signingSecret)) {
            signer = new HeaderSigner(signingSecret);
            log.info("Gateway identity headers must carry a valid HMAC signature");
        } else {
            log.warn("gateway.identity.signing-secret is NOT set: X-User-* headers are trusted without a signature. "
                    + "Only safe if this service is unreachable except through the API gateway.");
        }
        return new GatewayAuthenticationFilter(signer, maxSkewSeconds);
    }

    /**
     * The filter is a servlet Filter bean, which Spring Boot would otherwise ALSO register in the plain servlet
     * chain (outside Spring Security, where its SecurityContext would be discarded). It must only run inside the
     * SecurityFilterChain, so the automatic registration is switched off.
     */
    @Bean
    public FilterRegistrationBean<GatewayAuthenticationFilter> gatewayAuthenticationFilterRegistration(
            GatewayAuthenticationFilter filter) {
        FilterRegistrationBean<GatewayAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public GatewaySecurityErrorHandler gatewaySecurityErrorHandler(ObjectMapper objectMapper) {
        return new GatewaySecurityErrorHandler(objectMapper);
    }

    @Configuration
    @ConditionalOnClass(name = "org.springframework.cloud.client.loadbalancer.LoadBalanced")
    static class LoadBalancedIdentityPropagationConfiguration {

        // static: BeanPostProcessors must be instantiated before the rest of the configuration
        @Bean
        static IdentityPropagationBeanPostProcessor identityPropagationBeanPostProcessor(
                ConfigurableListableBeanFactory beanFactory) {
            return new IdentityPropagationBeanPostProcessor(beanFactory);
        }
    }
}
