package org.praskanth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderItemResponse {
    private String productId;
    private String productName;
    private String variantId;
    /** "Blue / M"; null for a product without variants */
    private String variantLabel;
    /** main picture of the product when it was ordered; null when it had none */
    private String productImage;
    private Integer quantity;
    private BigDecimal unitPrice;
}
