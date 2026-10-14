package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.KycStatusUpdateRequest;
import org.praskanth.dto.SellerProfileRequest;
import org.praskanth.dto.SellerPublicResponse;
import org.praskanth.dto.SellerRegisterRequest;
import org.praskanth.dto.SellerResponse;
import org.praskanth.dto.SellerSummaryResponse;
import org.praskanth.entity.SellerProfile;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.SellerService;
import org.praskanth.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * Seller sign-up and management.
 *   public:  POST /register, GET /{id}/public
 *   user:    POST /onboard (any signed-in user becomes a seller)
 *   SELLER:  GET|PUT /me (identity always comes from the token, never from a path id)
 *   ADMIN / SUPPORT_AGENT: list + detail; ADMIN only: KYC status changes
 *
 * Seller sign-up flow: POST /api/v1/auth/register/initiate -> /register/verify-otp -> POST /api/v1/sellers/register
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sellers")
public class SellerController {

    private static final Set<String> SELLER_SORT_FIELDS = Set.of("id", "kycStatus", "createdAt");

    private final SellerService sellerService;

    // ---------- registration ----------

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<SellerResponse>> register(@Valid @RequestBody SellerRegisterRequest request) {
        SellerResponse response = sellerService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Seller registration submitted. Your account will be activated after KYC review.", response));
    }

    @PostMapping("/onboard")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SellerResponse>> onboard(@AuthenticationPrincipal GatewayUser me,
                                                               @Valid @RequestBody SellerProfileRequest request) {
        SellerResponse response = sellerService.onboardExistingUser(me.id(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                "Seller profile submitted for KYC review. Refresh your session to receive seller permissions.", response));
    }

    // ---------- the seller themself ----------

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('SELLER')")
    public ResponseEntity<ApiResponse<SellerResponse>> getMyProfile(@AuthenticationPrincipal GatewayUser me) {
        return ResponseEntity.ok(ApiResponse.success("Seller profile fetched successfully",
                sellerService.getMyProfile(me.id())));
    }

    @PutMapping("/me")
    @PreAuthorize("hasAuthority('SELLER')")
    public ResponseEntity<ApiResponse<SellerResponse>> updateMyProfile(@AuthenticationPrincipal GatewayUser me,
                                                                       @Valid @RequestBody SellerProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Seller profile updated successfully",
                sellerService.updateMyProfile(me.id(), request)));
    }

    // ---------- storefront (public) ----------

    @GetMapping("/{sellerId}/public")
    public ResponseEntity<ApiResponse<SellerPublicResponse>> getPublicProfile(@PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.success(sellerService.getPublicProfile(sellerId)));
    }

    // ---------- ADMIN / SUPPORT ----------

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPPORT_AGENT')")
    public ResponseEntity<ApiResponse<Page<SellerSummaryResponse>>> listSellers(
            @RequestParam(required = false) SellerProfile.KycStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        var pageable = PageRequests.of(page, size, sortBy, direction, SELLER_SORT_FIELDS, "createdAt");
        return ResponseEntity.ok(ApiResponse.success(sellerService.listSellers(status, pageable)));
    }

    @GetMapping("/{sellerId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPPORT_AGENT')")
    public ResponseEntity<ApiResponse<SellerResponse>> getSeller(@PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.success("Seller fetched successfully", sellerService.getSellerById(sellerId)));
    }

    // ---------- ADMIN only ----------

    @PutMapping("/{sellerId}/kyc-status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<SellerResponse>> updateKycStatus(@PathVariable Long sellerId,
                                                                       @Valid @RequestBody KycStatusUpdateRequest request,
                                                                       @AuthenticationPrincipal GatewayUser admin) {
        return ResponseEntity.ok(ApiResponse.success("Seller status updated successfully",
                sellerService.updateKycStatus(sellerId, request, admin.id())));
    }
}
