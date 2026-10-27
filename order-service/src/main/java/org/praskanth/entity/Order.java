package org.praskanth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** References user-service's MySQL user id - a different database, so no FK constraint. */
    @Column(nullable = false)
    private Long userId;

    private Long sellerId;

    /** Who to e-mail about this order: a snapshot taken when it was placed. Null on old orders (they get no e-mails). */
    @Column(length = 255)
    private String customerEmail;

    @Column(length = 255)
    private String customerName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    /** Populated when status transitions to CANCELLED (insufficient stock or declined payment). */
    private String cancellationReason;

    @Builder.Default
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    // Snapshot of delivery address
    private Long shippingAddressId;
    private String recipientName;
    private String shippingAddress;

    private BigDecimal totalMrpPrice;

    private BigDecimal totalSellingPrice;

    private Integer discount;

    /** Coupon used on this checkout (null = none) and this order's share of its discount, in money. */
    @Column(length = 50)
    private String couponCode;

    @Column(precision = 12, scale = 2)
    private BigDecimal couponDiscount;

    /** Id shared by all orders created by one "Place order" click (one per seller); links them to ONE coupon redemption. */
    @Column(length = 64)
    private String checkoutRef;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant deliveryDate;

    /** RAZORPAY, STRIPE or COD. Null until the customer has paid / chosen cash on delivery. */
    @Column(length = 20)
    private String paymentMethod;

    /**
     * Until when the customer may still pay. Stock is reserved for this long; after it the order is cancelled
     * and the stock goes back (OrderService.expireUnpaidOrders). Null on old orders and once the order is confirmed.
     */
    private Instant expiresAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.deliveryDate == null) {
            this.deliveryDate = this.createdAt.plus(7, ChronoUnit.DAYS);
        }
    }
}
