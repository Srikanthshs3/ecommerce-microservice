package org.praskanth.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A discount code. {@code sellerId == null} means a platform coupon (created by an admin) that applies to the whole
 * cart; a seller coupon only discounts the items of that seller.
 */
@Entity
@Table(name = "coupons",
        uniqueConstraints = @UniqueConstraint(name = "uk_coupon_code", columnNames = "code"),
        indexes = @Index(name = "idx_coupon_seller", columnList = "seller_id"))
@Getter
@Setter
@NoArgsConstructor
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Always stored upper-case. */
    @Column(nullable = false, length = 30)
    private String code;

    @Column(length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal discountValue;

    /** Upper limit of a PERCENTAGE discount; null = no cap. */
    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    /** The eligible items must add up to at least this much; null = no minimum. */
    @Column(name = "min_order_amount", precision = 12, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    /** null = never expires */
    @Column(name = "valid_until")
    private Instant validUntil;

    /** Total number of checkouts that may use it; null = unlimited. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    /** How many times one customer may use it; null = unlimited. */
    @Column(name = "per_user_limit")
    private Integer perUserLimit;

    /** Redemptions currently counting against the limits (RESERVED). */
    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(nullable = false)
    private boolean active = true;

    /** true = shown in customers' "available coupons" list; false = a private code that must be typed in. */
    @Column(nullable = false)
    private boolean listed;

    @Column(name = "seller_id")
    private Long sellerId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
