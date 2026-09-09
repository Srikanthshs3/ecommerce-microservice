package org.praskanth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row per (orderId, productId) reservation. This exists because PaymentFailedEvent /
 * InventoryReservationFailedEvent carry only an orderId - without this ledger, inventory-service
 * would have no way to know which products/quantities to release for that order. The Inventory
 * entity itself only tracks aggregate available/reserved counts, not "reserved by whom".
 */
@Entity
@Table(name = "order_reservations", uniqueConstraints = @UniqueConstraint(columnNames = {"order_id", "product_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private String productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public enum ReservationStatus {
        RESERVED,
        RELEASED,
        CONFIRMED
    }
}
