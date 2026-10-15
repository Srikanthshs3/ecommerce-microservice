package org.praskanth.dto;

import java.time.Instant;
import java.util.List;

/** Full seller view (owner and admins). Sensitive values are masked. */
public record SellerResponse(
        Long id,
        String email,
        String ownerName,
        String phoneNumber,
        String gstin,
        String panMasked,
        String kycStatus,
        String statusReason,
        Instant kycReviewedAt,
        Business business,
        Bank bank,
        List<AddressResponse> addresses,
        Instant createdAt,
        Instant updatedAt
) {
    public record Business(String businessName, String legalName, String businessAddress,
                           String businessPhone, String businessEmail, String logo, String banner) {}

    public record Bank(String accountHolderName, String accountNumberMasked, String ifscCode) {}
}
