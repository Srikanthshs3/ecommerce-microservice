package org.praskanth.mapper;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.SellerPublicResponse;
import org.praskanth.dto.SellerResponse;
import org.praskanth.dto.SellerSummaryResponse;
import org.praskanth.entity.BankDetails;
import org.praskanth.entity.BusinessDetails;
import org.praskanth.entity.SellerProfile;
import org.praskanth.entity.User;
import org.praskanth.util.MaskingUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SellerMapper {

    private final AddressMapper addressMapper;

    /** Call inside a transaction: touches the lazy user (and addresses when requested). */
    public SellerResponse toResponse(SellerProfile sp, boolean includeAddresses) {
        User owner = sp.getUser();
        BusinessDetails b = sp.getBusinessDetails();
        BankDetails bank = sp.getBankDetails();
        return new SellerResponse(
                sp.getId(),
                owner != null ? owner.getEmail() : null,
                owner != null ? owner.getName() : null,
                owner != null ? owner.getPhoneNumber() : null,
                sp.getGstin(),
                MaskingUtils.maskPan(sp.getPan()),
                sp.getKycStatus() != null ? sp.getKycStatus().name() : null,
                sp.getStatusReason(),
                sp.getKycReviewedAt(),
                b == null ? null : new SellerResponse.Business(b.getBusinessName(), b.getLegalName(),
                        b.getBusinessAddress(), b.getBusinessPhone(), b.getBusinessEmail(), b.getLogo(), b.getBanner()),
                bank == null ? null : new SellerResponse.Bank(bank.getAccountHolderName(),
                        MaskingUtils.maskAccountNumber(bank.getAccountNumber()), bank.getIfscCode()),
                includeAddresses ? addressMapper.toResponses(sp.getAddresses()) : null,
                sp.getCreatedAt(),
                sp.getUpdatedAt());
    }

    public SellerSummaryResponse toSummary(SellerProfile sp) {
        BusinessDetails b = sp.getBusinessDetails();
        return new SellerSummaryResponse(
                sp.getId(),
                b != null ? b.getBusinessName() : null,
                b != null ? b.getLegalName() : null,
                sp.getGstin(),
                sp.getKycStatus() != null ? sp.getKycStatus().name() : null,
                sp.getCreatedAt());
    }

    public SellerPublicResponse toPublic(SellerProfile sp) {
        BusinessDetails b = sp.getBusinessDetails();
        return new SellerPublicResponse(sp.getId(), b.getBusinessName(), b.getLogo(), b.getBanner(), sp.getCreatedAt());
    }
}
