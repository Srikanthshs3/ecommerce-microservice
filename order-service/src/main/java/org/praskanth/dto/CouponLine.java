package org.praskanth.dto;

import java.math.BigDecimal;

/** What the customer pays for one seller's items. */
public record CouponLine(Long sellerId, BigDecimal amount) {
}
