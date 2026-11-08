package org.praskanth.resilience;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;

/**
 * Loads {@code resilience-defaults.yml} (shipped inside common-library) as the LOWEST-priority property source, so every
 * service starts with sensible circuit breaker / retry / bulkhead / time limiter settings and anything in the config
 * repo (APPLICATION.yaml, SERVICE-NAME.yml) or an environment variable overrides it.
 *
 * Why not put these settings in the config repo's APPLICATION.yaml? That file is also served to the API gateway, which
 * does not have common-library; the exception class names listed here would not exist there and the gateway would fail
 * to start.
 */
public class ResilienceDefaultsPostProcessor implements EnvironmentPostProcessor {

    private static final String RESOURCE = "resilience-defaults.yml";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        if (!resource.exists()) {
            return;
        }
        try {
            List<PropertySource<?>> sources = new YamlPropertySourceLoader().load("resilienceDefaults", resource);
            sources.forEach(source -> environment.getPropertySources().addLast(source));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }
}
