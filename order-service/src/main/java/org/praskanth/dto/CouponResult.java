package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** coupon-service's answer to redeem. valid=false is a normal outcome: message says why. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CouponResult {
    private boolean valid;
    private String code;
    private String message;
    private BigDecimal discountAmount;
    private List<Allocation> allocations;

    /** The part of the discount that belongs to one seller's order. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Allocation {
        private Long sellerId;
        private BigDecimal amount;
    }
}
