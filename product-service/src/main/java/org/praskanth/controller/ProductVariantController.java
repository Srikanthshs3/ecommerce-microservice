package org.praskanth.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.client.InventoryApiClient;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.ProductResponse;
import org.praskanth.dto.VariantDtos;
import org.praskanth.service.interfc.ProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Public: how many pieces of each variant can be bought right now (the product page greys out sold-out options). */
@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
@Slf4j
public class ProductVariantController {

    private final ProductService productService;
    private final InventoryApiClient inventoryApiClient;

    /** {"v3f9a2c1d": 12, "v81bb0c2e": 0}, keyed by variant id. Empty for a simple product, or when stock is unknown. */
    @GetMapping("/{id}/availability")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> availability(@PathVariable String id) {
        ProductResponse product = productService.getProductById(id);
        List<VariantDtos.VariantResponse> variants = product.variants() == null ? List.of() : product.variants();
        Map<String, Integer> byVariant = new HashMap<>();
        if (!variants.isEmpty()) {
            try {
                Map<String, Integer> byStockId = inventoryApiClient.availability(variants.stream().map(VariantDtos.VariantResponse::stockId).toList());
                for (VariantDtos.VariantResponse v : variants) {
                    // a variant whose stock row does not exist yet (it is created a moment after the product) counts as 0
                    byVariant.put(v.id(), byStockId.getOrDefault(v.stockId(), 0));
                }
            } catch (RuntimeException e) {
                log.warn("Could not read availability for product {}: {}", id, e.getMessage());
                return ResponseEntity.ok(ApiResponse.success(Map.of())); // unknown: the page treats it as "available"; the cart re-checks
            }
        }
        return ResponseEntity.ok(ApiResponse.success(byVariant));
    }
}
