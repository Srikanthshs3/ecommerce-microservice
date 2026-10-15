package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/** coupon-service's answer. valid=false is a normal outcome (message says why), not an HTTP error. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CouponResult {
    private boolean valid;
    private String code;
    private String message;
    private String description;
    private BigDecimal discountAmount;
}
