package org.praskanth.controller;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.ShipmentDtos.CancelRequest;
import org.praskanth.dto.ShipmentDtos.ShipmentView;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.ShippingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/shipping")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShippingService shippingService;

    /** Tracking page: status plus the timeline. Customer (own order), seller (own product), delivery partner or admin. */
    @GetMapping("/order/{orderId}")
    public ResponseEntity<ApiResponse<ShipmentView>> getByOrderId(@PathVariable Long orderId,
                                                                  @AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success(shippingService.getForOrder(orderId, caller)));
    }

    /** All shipments of the logged-in seller. */
    @GetMapping("/seller")
    public ResponseEntity<ApiResponse<List<ShipmentView>>> mine(@AuthenticationPrincipal GatewayUser caller) {
        if (!caller.hasRole("SELLER") && !caller.isAdmin()) {
            throw new ForbiddenOperationException("Sellers only.");
        }
        return ResponseEntity.ok(ApiResponse.success(shippingService.forSeller(caller)));
    }

    /** Seller: "packed, ready for a delivery agent". */
    @PostMapping("/order/{orderId}/ready")
    public ResponseEntity<ApiResponse<ShipmentView>> ready(@PathVariable Long orderId,
                                                           @AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success("Marked ready to ship", shippingService.markReadyToShip(orderId, caller)));
    }

    /** Seller (reason required) or the customer who owns the order: cancel before the parcel leaves. */
    @PostMapping("/order/{orderId}/cancel")
    public ResponseEntity<ApiResponse<ShipmentView>> cancel(@PathVariable Long orderId,
                                                            @RequestBody(required = false) CancelRequest request,
                                                            @AuthenticationPrincipal GatewayUser caller) {
        return ResponseEntity.ok(ApiResponse.success("Order cancelled",
                shippingService.cancel(orderId, request == null ? null : request.reason(), caller)));
    }
}
