package org.praskanth.resilience;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceConfigTest {

    @Test
    @DisplayName("timeout post-processor installs a timeout-bearing request factory on every RestTemplate")
    void restTemplateTimeouts_enabledByDefault_replacesRequestFactory() {
        // Given
        BeanPostProcessor processor = HttpTimeoutConfiguration.restTemplateTimeoutPostProcessor(new MockEnvironment());
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequestFactory before = restTemplate.getRequestFactory();
        // When
        Object result = processor.postProcessAfterInitialization(restTemplate, "rt");
        // Then
        assertThat(result).isSameAs(restTemplate);
        assertThat(restTemplate.getRequestFactory()).isNotSameAs(before).isInstanceOf(SimpleClientHttpRequestFactory.class);
    }

    @Test
    @DisplayName("timeout post-processor leaves RestTemplate alone when disabled")
    void restTemplateTimeouts_disabled_keepsRequestFactory() {
        MockEnvironment env = new MockEnvironment().withProperty("resilience.http.enabled", "false");
        BeanPostProcessor processor = HttpTimeoutConfiguration.restTemplateTimeoutPostProcessor(env);
        RestTemplate restTemplate = new RestTemplate();
        ClientHttpRequestFactory before = restTemplate.getRequestFactory();

        processor.postProcessAfterInitialization(restTemplate, "rt");

        assertThat(restTemplate.getRequestFactory()).isSameAs(before);
    }

    @Test
    @DisplayName("timeout post-processor ignores beans that are not RestTemplates")
    void restTemplateTimeouts_otherBean_returnedUntouched() {
        BeanPostProcessor processor = HttpTimeoutConfiguration.restTemplateTimeoutPostProcessor(new MockEnvironment());
        Object bean = new Object();

        assertThat(processor.postProcessAfterInitialization(bean, "other")).isSameAs(bean);
    }

    @Test
    @DisplayName("resilience defaults are added as the lowest-priority property source")
    void resilienceDefaults_addedLast() {
        // Given
        MockEnvironment env = new MockEnvironment();
        // When
        new ResilienceDefaultsPostProcessor().postProcessEnvironment(env, new SpringApplication());
        // Then
        assertThat(env.getPropertySources().contains("resilienceDefaults")).isTrue();
        assertThat(env.getProperty("resilience.http.connect-timeout")).isEqualTo("2s");
    }

    @Test
    @DisplayName("resilience defaults never override an explicitly configured value")
    void resilienceDefaults_doNotOverrideExistingProperties() {
        MockEnvironment env = new MockEnvironment().withProperty("resilience.http.connect-timeout", "9s");

        new ResilienceDefaultsPostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertThat(env.getProperty("resilience.http.connect-timeout")).isEqualTo("9s");
    }
}
