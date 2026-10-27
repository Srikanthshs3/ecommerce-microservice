package org.praskanth.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One sellable combination of a product's options, e.g. {Colour=Blue, Size=M}. It has its own price; its stock lives
 * in inventory-service under the key {@code productId:variantId}. A variant is never deleted, only switched off
 * ({@code active=false}), because carts and past orders may still refer to it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductVariant {

    /** Short, stable id such as "v3f9a2c1d"; part of the stock key, so it never changes. */
    private String id;

    /** Option name -> value, in the order of {@code Product.optionNames}. Mongo keeps the order. */
    @Builder.Default
    private Map<String, String> options = new LinkedHashMap<>();

    private BigDecimal mrpPrice;
    private BigDecimal sellingPrice;
    private BigDecimal discountPrice;

    @Builder.Default
    private boolean active = true;
}
