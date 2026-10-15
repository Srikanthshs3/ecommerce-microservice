package org.praskanth.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ProductResponse(String id,
                              String name,
                              String categoryId,
                              /** root..categoryId ids: lets a UI build a breadcrumb without extra calls */
                              List<String> categoryPath,
                              Long sellerId,
                              String description,
                              BigDecimal mrpPrice,
                              BigDecimal sellingPrice,
                              BigDecimal discountPrice,
                              List<String> imageUrl,
                              String color,
                              Instant createdAt) {
}
