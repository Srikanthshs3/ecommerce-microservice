package org.praskanth.controller;

import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.InventoryResponse;
import org.praskanth.dto.StockAdjustRequest;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    @GetMapping
    public ResponseEntity<ApiResponse<List<InventoryResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success(inventoryService.getAll()));
    }

    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    @PostMapping("/{productId}/restock")
    public ResponseEntity<ApiResponse<InventoryResponse>> restock(
            @PathVariable String productId, @Valid @RequestBody StockAdjustRequest request,
            @AuthenticationPrincipal GatewayUser user) {
        // a seller may only add stock to their own products (inventory rows carry the product's sellerId)
        InventoryResponse current = inventoryService.getByProductId(productId); // 404 if the row does not exist yet
        if (!user.isAdmin() && (current.getSellerId() == null || !current.getSellerId().equals(user.id()))) {
            throw new ForbiddenOperationException("You can only add stock to your own products");
        }
        return ResponseEntity.ok(ApiResponse.success("Stock added",
                inventoryService.restock(productId, request.getQuantity())));
    }

    /** Called by order-service (directly today, via the Saga orchestration in the next phase) when an order is placed. */
    @PreAuthorize("hasAuthority('ADMIN')") // saga step - normally driven by Kafka events, not by users
    @PostMapping("/{productId}/reserve")
    public ResponseEntity<ApiResponse<InventoryResponse>> reserve(
            @PathVariable String productId, @Valid @RequestBody StockAdjustRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Stock reserved",
                inventoryService.reserve(productId, request.getQuantity())));
    }

    /** Compensating action - releases a reservation back to available stock (e.g. payment failed). */
    @PreAuthorize("hasAuthority('ADMIN')") // saga step - normally driven by Kafka events, not by users
    @PostMapping("/{productId}/release")
    public ResponseEntity<ApiResponse<InventoryResponse>> release(
            @PathVariable String productId, @Valid @RequestBody StockAdjustRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Reservation released",
                inventoryService.release(productId, request.getQuantity())));
    }

    /** Finalizes a sale - reserved stock is permanently deducted. */
    @PreAuthorize("hasAuthority('ADMIN')") // saga step - normally driven by Kafka events, not by users
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
