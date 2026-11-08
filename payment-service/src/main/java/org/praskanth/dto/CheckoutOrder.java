package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;

/** payment-service's own (tolerant) view of an order: only the fields it needs. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CheckoutOrder {
    private Long id;
    private Long userId;
    private String status;
    private BigDecimal totalAmount;
    private String cancellationReason;
    private String checkoutRef;
    private Instant expiresAt;
}
