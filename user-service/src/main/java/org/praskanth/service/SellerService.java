package org.praskanth.service;

import org.praskanth.dto.KycStatusUpdateRequest;
import org.praskanth.dto.SellerProfileRequest;
import org.praskanth.dto.SellerPublicResponse;
import org.praskanth.dto.SellerRegisterRequest;
import org.praskanth.dto.SellerResponse;
import org.praskanth.dto.SellerSummaryResponse;
import org.praskanth.entity.SellerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Everything seller-specific: sign-up, onboarding an existing customer, own profile, storefront, and KYC review. */
public interface SellerService {

    /** New person: verified email + credentials + seller profile in one call. */
    SellerResponse register(SellerRegisterRequest request);

    /** Existing signed-in user (e.g. a customer) becomes a seller with the same login. */
    SellerResponse onboardExistingUser(Long userId, SellerProfileRequest request);

    SellerResponse getMyProfile(Long userId);

    SellerResponse updateMyProfile(Long userId, SellerProfileRequest request);

    /** Shopper-facing store info; only visible for ACTIVE sellers. */
    SellerPublicResponse getPublicProfile(Long sellerId);

    // --- admin / support ---
    Page<SellerSummaryResponse> listSellers(SellerProfile.KycStatus status, Pageable pageable);

    SellerResponse getSellerById(Long sellerId);

    SellerResponse updateKycStatus(Long sellerId, KycStatusUpdateRequest request, Long reviewerId);
}
