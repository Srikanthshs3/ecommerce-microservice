package org.praskanth.modal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String userEmail;

    @ManyToOne
    @JsonIgnore
    @JoinColumn(name = "cart_id")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Cart cart;

    private String productId;

    private String productName;

    private Long sellerId;

    private String imageUrl;

    /** Chosen variant of the product (null for simple products). Stock key = productId:variantId. */
    @Column(length = 40)
    private String variantId;

    /** "Blue / M" - copied at add-to-cart time, like the name and the price. */
    @Column(length = 200)
    private String variantLabel;

    private String size;

    private Integer quantity;

    private BigDecimal unitMrpPrice;
    private BigDecimal unitSellingPrice;

    private BigDecimal totalMrpPrice;
    private BigDecimal totalSellingPrice;
}
