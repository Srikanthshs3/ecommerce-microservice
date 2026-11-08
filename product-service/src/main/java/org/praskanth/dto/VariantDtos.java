package org.praskanth.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Variant shapes used in requests and responses. */
public final class VariantDtos {

    private VariantDtos() {
    }

    /**
     * What the seller sends for one variant. {@code id} is empty for a new combination; {@code stock} is the opening
     * stock of a NEW variant (live stock of an existing one is changed through inventory-service's restock).
     */
    public record VariantRequest(String id, Map<String, String> options, BigDecimal mrpPrice, BigDecimal sellingPrice,
                                 Integer stock) {
    }

    /** What the shop gets for one ACTIVE variant. {@code imageUrl} is already resolved (see VariantRules.resolveImages). */
    public record VariantResponse(String id,
                                  Map<String, String> options,
                                  String label,
                                  /** key of this variant's stock in inventory-service: productId:variantId */
                                  String stockId,
                                  BigDecimal mrpPrice,
                                  BigDecimal sellingPrice,
                                  BigDecimal discountPrice,
                                  List<String> imageUrl) {
    }

    public record GalleryResponse(String value, List<String> imageUrl) {
    }
}
