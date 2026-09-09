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

    private String size;

    @Column(nullable = false)
    private Integer quantity;

    private BigDecimal mrpPrice;

    private BigDecimal sellingPrice;

    private Long userId;

    private Long sellerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;
}
