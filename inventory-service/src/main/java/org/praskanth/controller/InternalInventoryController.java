package org.praskanth.controller;

import lombok.RequiredArgsConstructor;
import org.praskanth.entity.Inventory;
import org.praskanth.repository.InventoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service-to-service API. NOT reachable by clients: the API gateway rejects every /internal/** path, and no gateway
 * route points here. product-service uses it to show which variants of a product are in stock on the (public) product page.
 */
@RestController
@RequestMapping("/internal/inventory")
@RequiredArgsConstructor
public class InternalInventoryController {

    private static final int MAX_IDS = 200;

    private final InventoryRepository inventoryRepository;

    /** GET /internal/inventory/availability?ids=p1:v1,p1:v2  ->  {"p1:v1": 12, "p1:v2": 0}  (stock key -> available pieces) */
    @GetMapping("/availability")
    public Map<String, Integer> availability(@RequestParam String ids) {
        List<String> keys = Arrays.stream(ids.split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().limit(MAX_IDS).toList();
        Map<String, Integer> result = new HashMap<>();
        for (Inventory inventory : inventoryRepository.findByProductIdIn(keys)) {
            result.put(inventory.getProductId(), inventory.getAvailableQuantity());
        }
        return result;
    }
}
