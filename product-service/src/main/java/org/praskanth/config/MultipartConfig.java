package org.praskanth.config;

import jakarta.servlet.MultipartConfigElement;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Spring's default upload limit is 1 MB per file, which would reject ordinary phone photos with a confusing error.
 * Declared in code (not only in yaml) so uploads work even when the config-server serves a different yaml.
 */
@Configuration
@RequiredArgsConstructor
public class MultipartConfig {

    private final ProductImageProperties properties;

    @Bean
    public MultipartConfigElement multipartConfigElement() {
        long perFile = properties.getMaxSizeBytes();
        // a little headroom per file for the multipart framing; the real per-picture limit is enforced by ImageValidator
        long perRequest = (perFile + 4096L) * properties.getMaxCount();
        return new MultipartConfigElement("", perFile + 4096L, perRequest, 0);
    }
}
