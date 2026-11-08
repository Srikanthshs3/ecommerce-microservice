package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/** shipping-service's own tolerant view of an order (the fields it needs). */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderInfo {
    private Long id;
    private Long userId;
    private Long sellerId;
    private String status;
    private BigDecimal totalAmount;
    private String shippingAddress;
}
