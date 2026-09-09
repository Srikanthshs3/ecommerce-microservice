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

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant deliveryDate;

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
