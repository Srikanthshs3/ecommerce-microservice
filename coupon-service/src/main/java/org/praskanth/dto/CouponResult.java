package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * Answer of validate / redeem. A coupon that cannot be used is a normal business outcome, not an error:
 * HTTP 200 with {@code valid=false} and a customer-friendly {@code message}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CouponResult(boolean valid, String code, String message, String description,
                           BigDecimal discountAmount, List<CouponAllocation> allocations) {

    public static CouponResult invalid(String code, String message) {
        return new CouponResult(false, code, message, null, BigDecimal.ZERO, List.of());
    }

    public static CouponResult ok(String code, String description, BigDecimal discount, List<CouponAllocation> allocations) {
        return new CouponResult(true, code, "Coupon applied", description, discount, allocations);
    }
}
