package org.praskanth.dto;

import org.praskanth.entity.Coupon;
import org.praskanth.entity.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

public record CouponResponse(
        Long id, String code, String description, DiscountType discountType, BigDecimal discountValue,
        BigDecimal maxDiscountAmount, BigDecimal minOrderAmount, Instant validFrom, Instant validUntil,
        Integer usageLimit, Integer perUserLimit, int usedCount, boolean active, boolean listed, Long sellerId,
        /** INACTIVE | SCHEDULED | EXPIRED | EXHAUSTED | ACTIVE */
        String status, Instant createdAt) {

    public static CouponResponse from(Coupon c, Instant now) {
        String status;
        if (!c.isActive()) status = "INACTIVE";
        else if (c.getValidFrom().isAfter(now)) status = "SCHEDULED";
        else if (c.getValidUntil() != null && c.getValidUntil().isBefore(now)) status = "EXPIRED";
        else if (c.getUsageLimit() != null && c.getUsedCount() >= c.getUsageLimit()) status = "EXHAUSTED";
        else status = "ACTIVE";
        return new CouponResponse(c.getId(), c.getCode(), c.getDescription(), c.getDiscountType(), c.getDiscountValue(),
                c.getMaxDiscountAmount(), c.getMinOrderAmount(), c.getValidFrom(), c.getValidUntil(),
                c.getUsageLimit(), c.getPerUserLimit(), c.getUsedCount(), c.isActive(), c.isListed(), c.getSellerId(),
                status, c.getCreatedAt());
    }
}
