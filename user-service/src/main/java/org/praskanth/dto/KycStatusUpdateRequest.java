package org.praskanth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.praskanth.entity.SellerProfile;

public record KycStatusUpdateRequest(
        @NotNull(message = "KYC status is required")
        SellerProfile.KycStatus status,

        /** Mandatory for REJECTED, SUSPENDED and BANNED so the seller knows what to fix. */
        @Size(max = 500)
        String reason
) {}
