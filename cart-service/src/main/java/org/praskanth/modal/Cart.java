package org.praskanth.modal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String userEmail;

    @OneToMany(mappedBy = "cart",  cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CartItem> cartItems  = new HashSet<>();

    private BigDecimal totalSellingPrice;
    private Integer totalItems;
    private BigDecimal totalMrpPrice;
    private BigDecimal discount;
    private String couponCode;
    /** Amount (not percent) taken off by couponCode; recomputed on every cart change. null/0 = no coupon. */
    private BigDecimal couponDiscount;
    @Builder.Default
    private Instant updatedAt = Instant.now();

}
