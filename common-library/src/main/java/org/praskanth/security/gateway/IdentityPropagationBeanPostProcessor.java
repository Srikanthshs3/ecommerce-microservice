package org.praskanth.security.gateway;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Adds {@link IdentityPropagation#INTERCEPTOR} to every {@code @LoadBalanced} RestTemplate / RestClient.Builder.
 *
 * Scoping to @LoadBalanced is deliberate: those clients only ever talk to services registered in Eureka, so
 * the identity headers can never leak to a third party (e.g. the Stripe/Razorpay calls in payment-service).
 * Any client that is NOT load-balanced gets no headers unless a developer opts in explicitly.
 */
class IdentityPropagationBeanPostProcessor implements BeanPostProcessor {

    private final ConfigurableListableBeanFactory beanFactory;

    IdentityPropagationBeanPostProcessor(ConfigurableListableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof RestTemplate restTemplate && isLoadBalanced(beanName)) {
            List<ClientHttpRequestInterceptor> interceptors = new ArrayList<>(restTemplate.getInterceptors());
            if (!interceptors.contains(IdentityPropagation.INTERCEPTOR)) {
                interceptors.add(IdentityPropagation.INTERCEPTOR);
                restTemplate.setInterceptors(interceptors);
            }
        } else if (bean instanceof RestClient.Builder builder && isLoadBalanced(beanName)) {
            builder.requestInterceptor(IdentityPropagation.INTERCEPTOR);
        }
        return bean;
    }

    private boolean isLoadBalanced(String beanName) {
        return beanFactory.findAnnotationOnBean(beanName, LoadBalanced.class) != null;
    }
}
