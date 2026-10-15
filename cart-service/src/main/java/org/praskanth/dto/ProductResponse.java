package org.praskanth.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductResponse {

    private String productId;

    private String name;

    private Long sellerId;

    private Integer quantity = 1;

    private BigDecimal mrpPrice;

    private BigDecimal sellingPrice;

    private BigDecimal itemTotal;
}
