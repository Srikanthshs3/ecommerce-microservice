package org.praskanth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InventoryResponse {
    private String productId;
    private String productName;
    private Long sellerId;
    private Integer availableQuantity;
    private Integer reservedQuantity;
    private Instant updatedAt;
}
