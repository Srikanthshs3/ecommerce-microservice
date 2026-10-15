package org.praskanth.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.praskanth.entity.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;

/** Body of create / update (admin and seller). Extra cross-field rules are checked in CouponService. */
@Data
public class CouponRequest {

    /** Letters, digits, '-' and '_' (stored upper-case). Ignored on update: a code never changes. */
    @NotBlank
    @Pattern(regexp = "^[A-Za-z0-9_-]{3,30}$", message = "must be 3-30 letters, digits, '-' or '_'")
    private String code;

    @Size(max = 255)
    private String description;

    @NotNull
    private DiscountType discountType;

    @NotNull
    @DecimalMin(value = "0.01", message = "must be greater than 0")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal discountValue;

    @DecimalMin(value = "0.01", message = "must be greater than 0")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal maxDiscountAmount;

    @DecimalMin(value = "0.00")
    @Digits(integer = 10, fraction = 2)
    private BigDecimal minOrderAmount;

    /** default: now */
    private Instant validFrom;

    /** default: never expires */
    private Instant validUntil;

    @Min(1)
    private Integer usageLimit;

    @Min(1)
    private Integer perUserLimit;

    /** default true */
    private Boolean active;

    /** default false (a private code) */
    private Boolean listed;

    /** ADMIN only: restrict the coupon to one seller's products. A SELLER's coupons are always their own. */
    private Long sellerId;
}
