package org.praskanth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** {@code product.images.*} in application.yaml. Every value has a sensible default, so the yaml entries are optional. */
@Component
@ConfigurationProperties(prefix = "product.images")
@Data
public class ProductImageProperties {

    /** Where files are written. In Docker this is a mounted volume (see docker-compose.yml). */
    private String storageDir = "./uploads/products";

    /** Amazon allows 7-9 pictures per listing. */
    private int maxCount = 8;

    /** Largest accepted upload, per picture. */
    private long maxSizeBytes = 5L * 1024 * 1024;

    /** Pictures larger than this (longest side) are scaled down when stored: big enough for the zoom view. */
    private int mainMaxPx = 1200;

    /** Size of the small copy used on product cards, in the cart, at checkout and in order lists. */
    private int thumbMaxPx = 400;

    /** Safety net against "decompression bombs" (a tiny file that expands to a gigantic bitmap). */
    private long maxPixels = 40_000_000L;
}
