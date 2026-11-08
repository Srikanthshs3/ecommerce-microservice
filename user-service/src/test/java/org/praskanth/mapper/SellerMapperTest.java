package org.praskanth.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.dto.AddressResponse;
import org.praskanth.dto.SellerPublicResponse;
import org.praskanth.dto.SellerResponse;
import org.praskanth.dto.SellerSummaryResponse;
import org.praskanth.entity.Address;
import org.praskanth.entity.BankDetails;
import org.praskanth.entity.BusinessDetails;
import org.praskanth.entity.SellerProfile;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerMapperTest {

    @Mock
    private AddressMapper addressMapper;

    private SellerProfile profile() {
        User owner = TestData.user(7L, "seller@x.com", UserRole.SELLER);
        owner.setPhoneNumber("9876543210");
        return SellerProfile.builder()
                .id(7L)
                .user(owner)
                .gstin(TestData.GSTIN)
                .pan(TestData.PAN)
                .kycStatus(SellerProfile.KycStatus.ACTIVE)
                .statusReason("ok")
                .kycReviewedAt(Instant.parse("2024-01-01T00:00:00Z"))
                .createdAt(Instant.parse("2023-01-01T00:00:00Z"))
                .businessDetails(BusinessDetails.builder().businessName("Store").legalName("Legal")
                        .businessAddress("addr").businessPhone("123").businessEmail("b@x.com")
                        .logo("logo").banner("banner").build())
                .bankDetails(BankDetails.builder().accountHolderName("Holder")
                        .accountNumber("123456789012").ifscCode("HDFC0001234").build())
                .build();
    }

    @Test
    void toResponse_masksSensitiveFieldsAndSkipsAddresses() {
        SellerMapper mapper = new SellerMapper(addressMapper);

        SellerResponse r = mapper.toResponse(profile(), false);

        assertThat(r.id()).isEqualTo(7L);
        assertThat(r.email()).isEqualTo("seller@x.com");
        assertThat(r.ownerName()).isEqualTo("Test User");
        assertThat(r.phoneNumber()).isEqualTo("9876543210");
        assertThat(r.gstin()).isEqualTo(TestData.GSTIN);
        assertThat(r.panMasked()).isEqualTo("AB*******F");
        assertThat(r.kycStatus()).isEqualTo("ACTIVE");
        assertThat(r.statusReason()).isEqualTo("ok");
        assertThat(r.business().businessName()).isEqualTo("Store");
        assertThat(r.business().logo()).isEqualTo("logo");
        assertThat(r.bank().accountNumberMasked()).isEqualTo("********9012");
        assertThat(r.bank().ifscCode()).isEqualTo("HDFC0001234");
        assertThat(r.addresses()).isNull();
        verifyNoInteractions(addressMapper);
    }

    @Test
    void toResponse_includeAddresses_delegatesToAddressMapper() {
        SellerMapper mapper = new SellerMapper(addressMapper);
        SellerProfile sp = profile();
        List<Address> addresses = new ArrayList<>();
        sp.setAddresses(addresses);
        List<AddressResponse> mapped = List.of(AddressResponse.builder().id(1L).build());
        when(addressMapper.toResponses(addresses)).thenReturn(mapped);

        SellerResponse r = mapper.toResponse(sp, true);

        assertThat(r.addresses()).isEqualTo(mapped);
    }

    @Test
    void toResponse_nullOwnerBusinessBankAndStatus_mapsToNulls() {
        SellerMapper mapper = new SellerMapper(addressMapper);
        SellerProfile sp = SellerProfile.builder().id(1L).kycStatus(null).businessDetails(null).bankDetails(null).build();

        SellerResponse r = mapper.toResponse(sp, false);

        assertThat(r.email()).isNull();
        assertThat(r.ownerName()).isNull();
        assertThat(r.phoneNumber()).isNull();
        assertThat(r.kycStatus()).isNull();
        assertThat(r.business()).isNull();
        assertThat(r.bank()).isNull();
        assertThat(r.panMasked()).isNull();
    }

    @Test
    void toSummary_mapsBusinessFields() {
        SellerMapper mapper = new SellerMapper(addressMapper);

        SellerSummaryResponse s = mapper.toSummary(profile());

        assertThat(s.id()).isEqualTo(7L);
        assertThat(s.storeName()).isEqualTo("Store");
        assertThat(s.legalName()).isEqualTo("Legal");
        assertThat(s.gstin()).isEqualTo(TestData.GSTIN);
        assertThat(s.kycStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void toSummary_nullBusinessAndStatus_mapsToNulls() {
        SellerMapper mapper = new SellerMapper(addressMapper);
        SellerProfile sp = SellerProfile.builder().id(2L).kycStatus(null).businessDetails(null).build();

        SellerSummaryResponse s = mapper.toSummary(sp);

        assertThat(s.storeName()).isNull();
        assertThat(s.legalName()).isNull();
        assertThat(s.kycStatus()).isNull();
    }

    @Test
    void toPublic_exposesOnlyStorefrontData() {
        SellerMapper mapper = new SellerMapper(addressMapper);

        SellerPublicResponse p = mapper.toPublic(profile());

        assertThat(p.sellerId()).isEqualTo(7L);
        assertThat(p.storeName()).isEqualTo("Store");
        assertThat(p.logo()).isEqualTo("logo");
        assertThat(p.banner()).isEqualTo("banner");
        assertThat(p.memberSince()).isEqualTo(Instant.parse("2023-01-01T00:00:00Z"));
    }
}
