package org.praskanth.dto;

import java.math.BigDecimal;

/** How much of the discount belongs to one seller's items (their order gets this much off). */
public record CouponAllocation(Long sellerId, BigDecimal amount) {
}
