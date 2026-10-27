package org.praskanth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "order_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Order order;

    /** References product-service's MongoDB _id - deliberately a String, not a foreign key, since it lives in a different datastore. */
    @Column(nullable = false)
    private String productId;

    private String productName;

    /** Picture of the product at the moment it was ordered (a snapshot, like the name and price). */
    @Column(length = 500)
    private String productImage;

    /** Chosen variant of the product (null for a product without variants). */
    @Column(length = 40)
    private String variantId;

    /** "Blue / M" at the time of ordering. */
    @Column(length = 200)
    private String variantLabel;

    private String size;

    @Column(nullable = false)
    private Integer quantity;

    private BigDecimal mrpPrice;

    private BigDecimal sellingPrice;

    private Long userId;

    private Long sellerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    /**
     * The key under which inventory-service keeps the stock of this line: the variant's own stock for a variant
     * (productId:variantId), the product's for a simple product. This is what travels in the order event.
     */
    public String stockId() {
        return variantId == null || variantId.isBlank() ? productId : productId + ":" + variantId;
    }
}
