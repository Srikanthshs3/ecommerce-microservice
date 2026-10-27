package org.praskanth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One parcel per order. Columns added in Stage 2 are nullable (rows from before have no value for them).
 * The status column is a plain varchar on purpose: MySQL "enum" columns that Hibernate generates reject new values.
 * @Version guards concurrent changes (seller cancels while an agent picks up: exactly one wins).
 */
@Entity
@Table(name = "shipments", uniqueConstraints = @UniqueConstraint(columnNames = "order_id"))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** References order-service's Postgres order id - a different database, so no FK constraint. */
    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(nullable = false, unique = true)
    private String trackingNumber;

    @Column(nullable = false)
    private String carrier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(30)")
    private ShipmentStatus status;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    // ---- Stage 2 (nullable) ----
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "seller_id")
    private Long sellerId;

    /** 6-digit pincode taken from the address when the shipment is created; delivery partners see parcels by pincode. */
    @Column(name = "shipping_pincode", length = 6)
    private String shippingPincode;

    @Column(name = "shipping_address", length = 1000)
    private String shippingAddress;

    /** RAZORPAY / STRIPE / COD. For COD the agent must collect cashToCollect at the door. */
    @Column(name = "payment_method", length = 20)
    private String paymentMethod;

    @Column(name = "cash_to_collect", precision = 12, scale = 2)
    private BigDecimal cashToCollect;

    @Column(name = "updated_at")
    private Instant updatedAt;

    // ---- Stage 3: delivery agent ----
    /** The agent who has the parcel right now (null while it is with the seller or back in the pool). */
    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "picked_up_at")
    private Instant pickedUpAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    /** Changes at every pickup, so the delivery code of an earlier attempt stops working. Nothing secret is stored. */
    @Builder.Default
    @Column(name = "otp_version", columnDefinition = "int not null default 0")
    private int otpVersion = 0;

    /** Wrong delivery codes typed in the current attempt. */
    @Builder.Default
    @Column(name = "otp_attempts", columnDefinition = "int not null default 0")
    private int otpAttempts = 0;

    @Builder.Default
    @Column(name = "failed_attempts", columnDefinition = "int not null default 0")
    private int failedAttempts = 0;

    /** Cash on delivery: true once the agent confirmed the money was collected. */
    @Column(name = "cash_collected")
    private Boolean cashCollected;

    @Version
    @Column(name = "version", columnDefinition = "bigint not null default 0")
    private long version;
}
