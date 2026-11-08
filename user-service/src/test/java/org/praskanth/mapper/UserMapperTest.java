package org.praskanth.mapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.dto.AddressResponse;
import org.praskanth.dto.UserResponse;
import org.praskanth.entity.Address;
import org.praskanth.entity.CustomerProfile;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserMapperTest {

    @Mock
    private AddressMapper addressMapper;

    @Test
    void roleNames_returnsRoleNameStrings() {
        User u = TestData.user(1L, "a@b.com", UserRole.CUSTOMER, UserRole.SELLER);

        assertThat(UserMapper.roleNames(u)).containsExactlyInAnyOrder("CUSTOMER", "SELLER");
    }

    @Test
    void primaryRole_picksHighestPrivilege() {
        assertThat(UserMapper.primaryRole(Set.of("CUSTOMER", "SELLER", "ADMIN"))).isEqualTo("ADMIN");
        assertThat(UserMapper.primaryRole(Set.of("CUSTOMER", "SELLER"))).isEqualTo("SELLER");
        assertThat(UserMapper.primaryRole(Set.of("CUSTOMER", "DELIVERY_PARTNER"))).isEqualTo("DELIVERY_PARTNER");
        assertThat(UserMapper.primaryRole(Set.of("SUPPORT_AGENT", "DELIVERY_PARTNER"))).isEqualTo("SUPPORT_AGENT");
        assertThat(UserMapper.primaryRole(Set.of("CUSTOMER"))).isEqualTo("CUSTOMER");
    }

    @Test
    void primaryRole_unknownOrEmpty_returnsUnknown() {
        assertThat(UserMapper.primaryRole(Set.of())).isEqualTo("UNKNOWN");
        assertThat(UserMapper.primaryRole(Set.of("WIZARD"))).isEqualTo("UNKNOWN");
    }

    @Test
    void toResponse_withoutAddresses_doesNotTouchAddressMapper() {
        UserMapper mapper = new UserMapper(addressMapper);
        User u = TestData.user(1L, "a@b.com", UserRole.CUSTOMER);
        u.setPhoneNumber("9999999999");
        u.setEmailVerified(true);
        u.setCustomerProfile(CustomerProfile.builder().user(u).build());

        UserResponse r = mapper.toResponse(u, false);

        assertThat(r.getId()).isEqualTo(1L);
        assertThat(r.getEmail()).isEqualTo("a@b.com");
        assertThat(r.getName()).isEqualTo("Test User");
        assertThat(r.getPhoneNumber()).isEqualTo("9999999999");
        assertThat(r.getRole()).containsExactly("CUSTOMER");
        assertThat(r.isEmailVerified()).isTrue();
        assertThat(r.isAccountNonLocked()).isTrue();
        assertThat(r.getAddresses()).isNull();
        verifyNoInteractions(addressMapper);
    }

    @Test
    void toResponse_withAddressesAndCustomerProfile_mapsAddresses() {
        UserMapper mapper = new UserMapper(addressMapper);
        User u = TestData.user(1L, "a@b.com", UserRole.CUSTOMER);
        List<Address> addresses = List.of(Address.builder().id(1L).build());
        u.setCustomerProfile(CustomerProfile.builder().user(u).addresses(addresses).build());
        List<AddressResponse> mapped = List.of(AddressResponse.builder().id(1L).build());
        org.mockito.Mockito.when(addressMapper.toResponses(addresses)).thenReturn(mapped);

        UserResponse r = mapper.toResponse(u, true);

        assertThat(r.getAddresses()).isEqualTo(mapped);
    }

    @Test
    void toResponse_withAddressesButNoCustomerProfile_leavesAddressesNull() {
        UserMapper mapper = new UserMapper(addressMapper);
        User u = TestData.user(1L, "a@b.com", UserRole.ADMIN);

        UserResponse r = mapper.toResponse(u, true);

        assertThat(r.getAddresses()).isNull();
        verifyNoInteractions(addressMapper);
    }
}
