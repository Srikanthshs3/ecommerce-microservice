package org.praskanth.service;

import org.junit.jupiter.api.Test;
import org.praskanth.dto.CouponAllocation;
import org.praskanth.dto.CouponLine;
import org.praskanth.entity.Coupon;
import org.praskanth.entity.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CouponEngineTest {

    static Coupon coupon(DiscountType type, String value) {
        Coupon c = new Coupon();
        c.setCode("TEST");
        c.setDiscountType(type);
        c.setDiscountValue(new BigDecimal(value));
        c.setValidFrom(Instant.now().minusSeconds(60));
        c.setActive(true);
        return c;
    }

    static CouponLine line(long seller, String amount) {
        return new CouponLine(seller, new BigDecimal(amount));
    }

    @Test
    void percentageOfTheWholeCart() {
        var result = CouponEngine.calculate(coupon(DiscountType.PERCENTAGE, "10"), List.of(line(1, "999.99")));
        assertEquals(new BigDecimal("100.00"), result.discount()); // 99.999 rounds half-up
    }

    @Test
    void percentageIsCappedByMaxDiscount() {
        Coupon c = coupon(DiscountType.PERCENTAGE, "10");
        c.setMaxDiscountAmount(new BigDecimal("50"));
        var result = CouponEngine.calculate(c, List.of(line(1, "1000")));
        assertEquals(0, new BigDecimal("50").compareTo(result.discount()));
    }

    @Test
    void fixedDiscountNeverExceedsTheCart() {
        var result = CouponEngine.calculate(coupon(DiscountType.FIXED, "500"), List.of(line(1, "300")));
        assertEquals(0, new BigDecimal("300").compareTo(result.discount()));
    }

    @Test
    void sellerCouponOnlyDiscountsThatSellersItems() {
        Coupon c = coupon(DiscountType.PERCENTAGE, "10");
        c.setSellerId(2L);
        var result = CouponEngine.calculate(c, List.of(line(1, "600"), line(2, "400")));
        assertEquals(0, new BigDecimal("40").compareTo(result.discount()));
        assertEquals(1, result.allocations().size());
        assertEquals(2L, result.allocations().get(0).sellerId().longValue());
    }

    @Test
    void allocationsAlwaysAddUpToTheDiscountExactly() {
        var result = CouponEngine.calculate(coupon(DiscountType.FIXED, "100"),
                List.of(line(1, "333.33"), line(2, "333.33"), line(3, "333.34")));
        BigDecimal sum = result.allocations().stream().map(CouponAllocation::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, new BigDecimal("100").compareTo(sum));
        assertTrue(result.allocations().stream().allMatch(a -> a.amount().signum() >= 0));
        assertEquals(3, result.allocations().size());
    }

    @Test
    void noEligibleItemsMeansNoDiscount() {
        Coupon c = coupon(DiscountType.FIXED, "50");
        c.setSellerId(9L);
        var result = CouponEngine.calculate(c, List.of(line(1, "600")));
        assertEquals(0, result.discount().signum());
        assertTrue(result.allocations().isEmpty());
    }
}
