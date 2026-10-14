package org.praskanth.dto;

import org.praskanth.entity.Coupon;
import org.praskanth.entity.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

/** What a customer sees in the "available coupons" list (no internal counters). */
public record AvailableCouponResponse(String code, String description, DiscountType discountType, BigDecimal discountValue,
                                      BigDecimal maxDiscountAmount, BigDecimal minOrderAmount, Instant validUntil, Long sellerId) {

    public static AvailableCouponResponse from(Coupon c) {
        return new AvailableCouponResponse(c.getCode(), c.getDescription(), c.getDiscountType(), c.getDiscountValue(),
                c.getMaxDiscountAmount(), c.getMinOrderAmount(), c.getValidUntil(), c.getSellerId());
    }
}
