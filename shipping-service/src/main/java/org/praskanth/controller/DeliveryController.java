package org.praskanth.controller;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.ShipmentDtos.DeliverRequest;
import org.praskanth.dto.ShipmentDtos.FailRequest;
import org.praskanth.dto.ShipmentDtos.PoolItem;
import org.praskanth.dto.ShipmentDtos.ShipmentView;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.DeliveryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Delivery-agent API (role DELIVERY_PARTNER, checked in DeliveryService). */
@RestController
@RequestMapping("/api/v1/shipping/agent")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService delivery;

    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<PoolItem>>> available(@AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success(delivery.pool(caller)));
    }

    /** active (default) = parcels I am carrying; status=done = my recent deliveries. */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<ShipmentView>>> mine(@RequestParam(defaultValue = "active") String status,
                                                                @AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success(delivery.mine(!"done".equals(status), caller)));
    }

    @PostMapping("/order/{orderId}/claim")
    public ResponseEntity<ApiResponse<ShipmentView>> claim(@PathVariable Long orderId, @AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success("Parcel taken", delivery.claim(orderId, caller)));
    }

    @PostMapping("/order/{orderId}/deliver")
    public ResponseEntity<ApiResponse<ShipmentView>> deliver(@PathVariable Long orderId, @RequestBody DeliverRequest request,
                                                             @AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success("Delivered", delivery.deliver(orderId, request.otp(), request.cashCollected(), caller)));
    }

    @PostMapping("/order/{orderId}/fail")
    public ResponseEntity<ApiResponse<ShipmentView>> fail(@PathVariable Long orderId, @RequestBody FailRequest request,
                                                          @AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success("Recorded", delivery.fail(orderId, request.reason(), caller)));
    }
}
