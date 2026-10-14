package org.praskanth.dto;

import java.util.List;

public record RedeemRequest(String checkoutRef, String code, List<CouponLine> lines) {
}
