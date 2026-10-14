package org.praskanth.service;

import org.junit.jupiter.api.Test;
import org.praskanth.entity.Coupon;
import org.praskanth.entity.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CouponPolicyTest {

    private final Instant now = Instant.parse("2026-10-10T12:00:00Z");

    private Coupon valid() {
        Coupon c = new Coupon();
        c.setCode("SAVE10");
        c.setDiscountType(DiscountType.PERCENTAGE);
        c.setDiscountValue(new BigDecimal("10"));
        c.setValidFrom(now.minusSeconds(3600));
        c.setActive(true);
        return c;
    }

    private Optional<String> check(Coupon c, String subtotal, long userUses) {
        return CouponPolicy.reject(c, now, new BigDecimal(subtotal), userUses);
    }

    @Test
    void aGoodCouponPasses() {
        assertTrue(check(valid(), "500", 0).isEmpty());
    }

    @Test
    void inactive() {
        Coupon c = valid();
        c.setActive(false);
        assertEquals("This coupon is not active.", check(c, "500", 0).orElseThrow());
    }

    @Test
    void notStartedYetAndExpired() {
        Coupon future = valid();
        future.setValidFrom(now.plusSeconds(10));
        assertEquals("This coupon is not valid yet.", check(future, "500", 0).orElseThrow());

        Coupon expired = valid();
        expired.setValidUntil(now.minusSeconds(10));
        assertEquals("This coupon has expired.", check(expired, "500", 0).orElseThrow());
    }

    @Test
    void totalUsageLimit() {
        Coupon c = valid();
        c.setUsageLimit(5);
        c.setUsedCount(5);
        assertEquals("This coupon has reached its usage limit.", check(c, "500", 0).orElseThrow());
        c.setUsedCount(4);
        assertTrue(check(c, "500", 0).isEmpty());
    }

    @Test
    void perUserLimit() {
        Coupon c = valid();
        c.setPerUserLimit(1);
        assertEquals("You have already used this coupon.", check(c, "500", 1).orElseThrow());
        c.setPerUserLimit(3);
        assertTrue(check(c, "500", 2).isPresent() == false);
        assertTrue(check(c, "500", 3).orElseThrow().contains("3 times"));
    }

    @Test
    void minimumOrderAmountSaysHowMuchMoreIsNeeded() {
        Coupon c = valid();
        c.setMinOrderAmount(new BigDecimal("500.00"));
        String message = check(c, "320", 0).orElseThrow();
        assertTrue(message.contains("₹180"), message);
        assertTrue(message.contains("₹500"), message);
        assertTrue(check(c, "500", 0).isEmpty());
    }

    @Test
    void sellerCouponWithNoMatchingItems() {
        Coupon c = valid();
        c.setSellerId(7L);
        assertTrue(check(c, "0", 0).orElseThrow().contains("particular seller"));
    }
}
