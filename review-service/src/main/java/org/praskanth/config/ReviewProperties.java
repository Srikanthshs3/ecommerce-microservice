package org.praskanth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "review")
@Data
public class ReviewProperties {

    private Images images = new Images();

    /** When true only customers who bought the product may review it. */
    private boolean requirePurchase = false;

    @Data
    public static class Images {
        private String storageDir = "./uploads/reviews";
        private int maxCount = 5;
        private long maxSizeBytes = 5L * 1024 * 1024;
    }
}
