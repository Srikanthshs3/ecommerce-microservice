package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.CouponResult;
import org.praskanth.dto.RedeemRequest;
import org.praskanth.dto.ReleaseRequest;
import org.praskanth.dto.ValidateRequest;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.RedemptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Service-to-service API. NOT exposed to clients: the API gateway rejects every /internal/** path.
 * The customer is whoever the forwarded X-User-* identity says (never a user id in the body).
 */
@RestController
@RequestMapping("/internal/v1/coupon")
@RequiredArgsConstructor
public class InternalCouponController {

    private final RedemptionService redemptionService;

    /** cart-service: "what would this code save on this cart?" Nothing is recorded. */
    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<CouponResult>> validate(@Valid @RequestBody ValidateRequest request,
                                                              @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success(redemptionService.validate(user, request)));
    }

    /** order-service: validate again, and use the coupon for this checkout (idempotent per checkoutRef). */
    @PostMapping("/redeem")
    public ResponseEntity<ApiResponse<CouponResult>> redeem(@Valid @RequestBody RedeemRequest request,
                                                            @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success(redemptionService.redeem(user, request)));
    }

    /** order-service: the checkout failed / was cancelled -> give the use back. No user (Kafka thread). */
    @PostMapping("/release")
    public ResponseEntity<ApiResponse<Void>> release(@Valid @RequestBody ReleaseRequest request) {
        redemptionService.release(request.checkoutRef());
        return ResponseEntity.ok(ApiResponse.success("Released", null));
    }
}
