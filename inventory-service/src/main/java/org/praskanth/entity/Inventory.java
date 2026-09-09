package org.praskanth.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "inventory", indexes = {@Index(name = "idx_inventory_product_id", columnList = "productId", unique = true),
        @Index(name = "idx_inventory_seller_id", columnList = "sellerId")})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** References product-service's MongoDB _id - a different datastore, so plain string, no FK. */
    @Column(name = "product_id", nullable = false, unique = true)
    private String productId;

    @Column(nullable = false)
    private String productName;

    @Column(nullable = false)
    private Long sellerId;

    @Builder.Default
    @Column(nullable = false)
    private Integer availableQuantity = 0;

    /** Stock that's been reserved (e.g. sitting in a pending order) but not yet deducted for good. */
    @Builder.Default
    @Column(nullable = false)
    private Integer reservedQuantity = 0;

    /**
     * Optimistic lock - reserve/release/confirm all read-modify-write this row, and under
     * concurrent requests for the same hot product we want a clean retry (OptimisticLockException)
     * rather than a lost update, not a row-level DB lock held across a network call.
     */
    @Version
    private Long version;

    @Builder.Default
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
