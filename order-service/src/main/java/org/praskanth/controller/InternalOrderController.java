package org.praskanth.controller;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.OrderResponse;
import org.praskanth.service.OrderService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API. NOT exposed to clients: the API gateway rejects every /internal/** path, and
 * network policy must keep it that way. Used by payment-service (from a Kafka consumer thread, i.e. with no
 * user and therefore no identity headers) to look up an order's amount.
 */
@RestController
@RequestMapping("/internal/v1/orders")
@RequiredArgsConstructor
public class InternalOrderController {

    private final OrderService orderService;

    /** All orders of one checkout (payment-service sums them up and checks each one's status). */
    @GetMapping("/checkout/{checkoutRef}")
    public ResponseEntity<ApiResponse<java.util.List<OrderResponse>>> getByCheckout(@PathVariable String checkoutRef) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getByCheckoutRef(checkoutRef)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(orderService.getById(id)));
    }
}
