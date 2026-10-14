package org.praskanth.dto;

import java.math.BigDecimal;

/** What the customer pays for one seller's items in the cart (input of coupon-service's validate). */
public record CouponLine(Long sellerId, BigDecimal amount) {
}
