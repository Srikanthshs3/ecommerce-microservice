package org.praskanth.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class CartItemResponse {
    private Long id;
    private String productId;
    private String productName;
    private String imageUrl;
    private String size;
    private Integer quantity;
    private Long sellerId;

    private BigDecimal unitMrpPrice;
    private BigDecimal unitSellingPrice;
    private BigDecimal totalMrpPrice;
    private BigDecimal totalSellingPrice;
}