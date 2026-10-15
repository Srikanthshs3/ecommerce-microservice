package org.praskanth.controller;

import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.ShipmentResponse;
import org.praskanth.exception.NotFoundException;
import org.praskanth.service.ShippingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShippingService shippingService;

    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<ShipmentResponse>> getByOrderId(@PathVariable Long orderId) {
        ShipmentResponse response = shippingService.getByOrderId(orderId);
        if (response == null) {
            throw new NotFoundException("No shipment recorded yet for order: " + orderId);
        }
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
