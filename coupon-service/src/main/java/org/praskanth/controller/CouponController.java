package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.AvailableCouponResponse;
import org.praskanth.dto.CouponRequest;
import org.praskanth.dto.CouponResponse;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.CouponService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Browser-facing API (through the gateway). */
@RestController
@RequestMapping("/api/v1/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<CouponResponse>> create(@Valid @RequestBody CouponRequest request,
                                                              @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Coupon created", couponService.create(user, request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<CouponResponse>> update(@PathVariable Long id, @Valid @RequestBody CouponRequest request,
                                                              @AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success("Coupon updated", couponService.update(user, id, request)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @AuthenticationPrincipal GatewayUser user) {
        couponService.delete(user, id);
        return ResponseEntity.ok(ApiResponse.success("Coupon deleted", null));
    }

    /** ADMIN: every coupon. SELLER: their own. */
    @GetMapping("/manage")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SELLER')")
    public ResponseEntity<ApiResponse<List<CouponResponse>>> manage(@AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success(couponService.listManageable(user)));
    }

    /** Coupons any logged-in customer may try (listed ones only; private codes are never shown). */
    @GetMapping("/available")
    public ResponseEntity<ApiResponse<List<AvailableCouponResponse>>> available(@AuthenticationPrincipal GatewayUser user) {
        return ResponseEntity.ok(ApiResponse.success(couponService.available(user)));
    }
}
