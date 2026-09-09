package org.praskanth.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record ProductResponse(String id,
                              String name,
                              String categoryId,
                              Long sellerId,
                              String description,
                              BigDecimal mrpPrice, BigDecimal sellingPrice, Instant createdAt) {
}
