package org.praskanth.dto;

import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class CartResponse {
    private Long id;
    private Long userId;
    private String userEmail;

    private List<CartItemResponse> cartItems;

    private BigDecimal totalSellingPrice;
    private BigDecimal totalMrpPrice;
    private Integer totalItems;
    private BigDecimal discount;
    private String couponCode;
}