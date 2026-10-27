package org.praskanth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OrderResponse {
    private Long id;
    private Long userId;
    private Long sellerId;
    private String status;
    private BigDecimal totalAmount;
    private String cancellationReason;
    private List<OrderItemResponse> items;
    private Instant orderDate;
    private String couponCode;
    /** This order's part of the coupon discount (already subtracted from totalAmount). */
    private BigDecimal couponDiscount;
    /** Shared by all orders (one per seller) created by one "Place order" click; the customer pays once per checkout. */
    private String checkoutRef;
    private String paymentMethod;
    /** Recipient, street, city ... as one line (a snapshot taken when the order was placed). */
    private String shippingAddress;
    /** Last moment to pay; after that the order is cancelled automatically. */
    private Instant expiresAt;
}
