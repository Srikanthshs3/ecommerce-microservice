package org.praskanth.controller;

import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.InventoryResponse;
import org.praskanth.dto.StockAdjustRequest;
import org.praskanth.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<InventoryResponse>> getByProductId(@PathVariable String productId) {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getByProductId(productId)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getAll()));
    }

    @PostMapping("/{productId}/restock")
    public ResponseEntity<ApiResponse<InventoryResponse>> restock(
            @PathVariable String productId, @Valid @RequestBody StockAdjustRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Stock added",
                inventoryService.restock(productId, request.getQuantity())));
    }

    /** Called by order-service (directly today, via the Saga orchestration in the next phase) when an order is placed. */
    @PostMapping("/{productId}/reserve")
    public ResponseEntity<ApiResponse<InventoryResponse>> reserve(
            @PathVariable String productId, @Valid @RequestBody StockAdjustRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Stock reserved",
                inventoryService.reserve(productId, request.getQuantity())));
    }

    /** Compensating action - releases a reservation back to available stock (e.g. payment failed). */
    @PostMapping("/{productId}/release")
    public ResponseEntity<ApiResponse<InventoryResponse>> release(
            @PathVariable String productId, @Valid @RequestBody StockAdjustRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Reservation released",
                inventoryService.release(productId, request.getQuantity())));
    }

    /** Finalizes a sale - reserved stock is permanently deducted. */
    @PostMapping("/{productId}/confirm")
    public ResponseEntity<ApiResponse<InventoryResponse>> confirm(
            @PathVariable String productId, @Valid @RequestBody StockAdjustRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Reservation confirmed",
                inventoryService.confirm(productId, request.getQuantity())));
    }

    @GetMapping("/{productId}/check")
    public ResponseEntity<Boolean> checkAvailability(
            @PathVariable String productId,
            @RequestParam Integer quantity
    ) {
        boolean isAvailable = inventoryService.checkAvailability(productId, quantity);
        return ResponseEntity.ok(isAvailable);
    }
}
