package org.praskanth.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.praskanth.entity.SellerProfile;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class KycStatusUpdateRequest {

    @NotNull(message = "KYC status is required")
    private SellerProfile.KycStatus kycStatus;
}
