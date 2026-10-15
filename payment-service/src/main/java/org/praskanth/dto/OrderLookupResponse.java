package org.praskanth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Deliberately NOT a shared module dependency on order-service's own OrderResponse - that would
 * couple these two services' deploy cycles together. This is payment-service's own view of
 * "the parts of an order I need", tolerant of extra fields via @JsonIgnoreProperties so
 * order-service can evolve its response freely as long as these two fields stay put.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class OrderLookupResponse {
    private Long id;
    private BigDecimal totalAmount;
}
