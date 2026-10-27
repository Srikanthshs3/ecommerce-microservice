package org.praskanth.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}

    /** 499.50 INR -> 49950 paise. Both Razorpay and Stripe want the smallest currency unit as a whole number. */
    public static long toMinor(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }
}
