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
    /** Money taken off by the coupon (0 when none). */
    private BigDecimal couponDiscount;
    /** totalSellingPrice - couponDiscount: what the customer pays for the items. */
    private BigDecimal payableAmount;
}