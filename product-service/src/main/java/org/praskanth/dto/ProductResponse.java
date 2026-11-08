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
                              Instant createdAt,
                              /** e.g. ["Colour","Size"]; empty for a simple product */
                              List<String> optionNames,
                              /** active variants only; empty for a simple product */
                              List<VariantDtos.VariantResponse> variants,
                              /** pictures per value of the first option (the seller's picture boxes) */
                              List<VariantDtos.GalleryResponse> galleries) {
}
