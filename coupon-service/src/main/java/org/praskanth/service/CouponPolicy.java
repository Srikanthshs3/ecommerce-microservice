package org.praskanth.service;

import org.praskanth.entity.Coupon;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/** Is this coupon usable right now by this customer on this cart? Returns the customer-friendly reason when not. */
public final class CouponPolicy {

    private CouponPolicy() {
    }

    /**
     * @param eligibleSubtotal what the coupon could discount in this cart (see CouponEngine)
     * @param userUses         how many times this customer already has an active redemption of the coupon
     */
    public static Optional<String> reject(Coupon coupon, Instant now, BigDecimal eligibleSubtotal, long userUses) {
        if (!coupon.isActive()) {
            return Optional.of("This coupon is not active.");
        }
        if (coupon.getValidFrom().isAfter(now)) {
            return Optional.of("This coupon is not valid yet.");
        }
        if (coupon.getValidUntil() != null && coupon.getValidUntil().isBefore(now)) {
            return Optional.of("This coupon has expired.");
        }
        if (coupon.getUsageLimit() != null && coupon.getUsedCount() >= coupon.getUsageLimit()) {
            return Optional.of("This coupon has reached its usage limit.");
        }
        if (coupon.getPerUserLimit() != null && userUses >= coupon.getPerUserLimit()) {
            return Optional.of(coupon.getPerUserLimit() == 1
                    ? "You have already used this coupon."
                    : "You have already used this coupon " + coupon.getPerUserLimit() + " times.");
        }
        if (eligibleSubtotal.signum() <= 0) {
            return Optional.of(coupon.getSellerId() != null
                    ? "This coupon only applies to products of a particular seller, and none are in your cart."
                    : "This coupon cannot be applied to the items in your cart.");
        }
        if (coupon.getMinOrderAmount() != null && eligibleSubtotal.compareTo(coupon.getMinOrderAmount()) < 0) {
            return Optional.of("Add items worth ₹" + plain(coupon.getMinOrderAmount().subtract(eligibleSubtotal))
                    + " more to use this coupon (minimum ₹" + plain(coupon.getMinOrderAmount()) + ").");
        }
        return Optional.empty();
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
