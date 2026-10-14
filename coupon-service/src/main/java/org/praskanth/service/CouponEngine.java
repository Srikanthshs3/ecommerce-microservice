package org.praskanth.service;

import org.praskanth.dto.CouponAllocation;
import org.praskanth.dto.CouponLine;
import org.praskanth.entity.Coupon;
import org.praskanth.entity.DiscountType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The money maths, with no Spring and no database so it is easy to test.
 *
 * Eligible amount = the whole cart for a platform coupon, or only that seller's items for a seller coupon.
 * Discount = PERCENTAGE: eligible x value / 100 (capped by maxDiscountAmount) | FIXED: value; never more than the
 * eligible amount. The discount is then split across the sellers' lines in proportion to their amounts, rounded
 * DOWN to paise, with the leftover paise given to the last line - so the parts always add up to the total exactly.
 * (Each seller gets their own order at checkout, and that order's total is reduced by its part.)
 */
public final class CouponEngine {

    public record Result(BigDecimal eligibleSubtotal, BigDecimal discount, List<CouponAllocation> allocations) {
    }

    private CouponEngine() {
    }

    public static BigDecimal eligibleSubtotal(Coupon coupon, List<CouponLine> lines) {
        return mergeEligible(coupon, lines).values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static Result calculate(Coupon coupon, List<CouponLine> lines) {
        Map<Long, BigDecimal> eligible = mergeEligible(coupon, lines);
        BigDecimal subtotal = eligible.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (subtotal.signum() <= 0) {
            return new Result(subtotal, BigDecimal.ZERO.setScale(2), List.of());
        }

        BigDecimal raw;
        if (coupon.getDiscountType() == DiscountType.PERCENTAGE) {
            raw = subtotal.multiply(coupon.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (coupon.getMaxDiscountAmount() != null) {
                raw = raw.min(coupon.getMaxDiscountAmount());
            }
        } else {
            raw = coupon.getDiscountValue();
        }
        BigDecimal discount = raw.min(subtotal).setScale(2, RoundingMode.HALF_UP);

        List<CouponAllocation> allocations = new ArrayList<>();
        BigDecimal given = BigDecimal.ZERO;
        int index = 0;
        for (Map.Entry<Long, BigDecimal> line : eligible.entrySet()) {
            index++;
            BigDecimal share;
            if (index == eligible.size()) {
                share = discount.subtract(given); // the last one takes the rounding leftovers
            } else {
                share = discount.multiply(line.getValue()).divide(subtotal, 2, RoundingMode.DOWN);
            }
            given = given.add(share);
            allocations.add(new CouponAllocation(line.getKey(), share));
        }
        return new Result(subtotal, discount, allocations);
    }

    /** sellerId -> amount, only the lines this coupon may discount, with positive amounts. */
    private static Map<Long, BigDecimal> mergeEligible(Coupon coupon, List<CouponLine> lines) {
        Map<Long, BigDecimal> merged = new LinkedHashMap<>();
        for (CouponLine line : lines) {
            if (line.amount() == null || line.amount().signum() <= 0) {
                continue;
            }
            if (coupon.getSellerId() != null && !coupon.getSellerId().equals(line.sellerId())) {
                continue;
            }
            merged.merge(line.sellerId(), line.amount(), BigDecimal::add);
        }
        return merged;
    }
}
