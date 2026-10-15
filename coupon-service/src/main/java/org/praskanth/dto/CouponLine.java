package org.praskanth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** The part of the cart that belongs to one seller: what the customer would pay for those items. */
public record CouponLine(@NotNull Long sellerId, @NotNull @PositiveOrZero BigDecimal amount) {
}
