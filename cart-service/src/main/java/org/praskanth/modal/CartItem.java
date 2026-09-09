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

    private String size;

    private Integer quantity;

    private BigDecimal unitMrpPrice;
    private BigDecimal unitSellingPrice;

    private BigDecimal totalMrpPrice;
    private BigDecimal totalSellingPrice;
}
