package org.praskanth.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ProductResponse {

    private String productId;

    private String name;

    private Long sellerId;

    private Integer quantity = 1;

    private BigDecimal mrpPrice;

    private BigDecimal sellingPrice;

    private BigDecimal itemTotal;

    /** product-service's picture list; the first entry is the main picture (kept on the cart line as a snapshot). */
    private List<String> imageUrl;

    /** Option names of a product with variants, e.g. ["Colour","Size"]; empty for a simple product. */
    private List<String> optionNames;

    /** The product's ACTIVE variants (product-service never sends switched-off ones). */
    private List<Variant> variants;

    @Data
    public static class Variant {
        private String id;
        private java.util.Map<String, String> options;
        private String label;
        private BigDecimal mrpPrice;
        private BigDecimal sellingPrice;
        private List<String> imageUrl;
    }
}
