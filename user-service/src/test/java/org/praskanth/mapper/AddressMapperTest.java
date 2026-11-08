package org.praskanth.mapper;

import org.junit.jupiter.api.Test;
import org.praskanth.dto.AddressResponse;
import org.praskanth.entity.Address;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AddressMapperTest {

    private final AddressMapper mapper = new AddressMapper();

    private Address address() {
        return Address.builder()
                .id(5L).recipientName("Rec").streetLine1("s1").streetLine2("s2").city("City").state("ST")
                .postalCode("560001").country("IN").isDefault(true).addressType(Address.AddressType.BILLING)
                .build();
    }

    @Test
    void toResponse_mapsAllFields() {
        AddressResponse r = mapper.toResponse(address());

        assertThat(r.getId()).isEqualTo(5L);
        assertThat(r.getRecipientName()).isEqualTo("Rec");
        assertThat(r.getStreetLine1()).isEqualTo("s1");
        assertThat(r.getStreetLine2()).isEqualTo("s2");
        assertThat(r.getCity()).isEqualTo("City");
        assertThat(r.getState()).isEqualTo("ST");
        assertThat(r.getPostalCode()).isEqualTo("560001");
        assertThat(r.getCountry()).isEqualTo("IN");
        assertThat(r.getAddressType()).isEqualTo("BILLING");
        assertThat(r.isDefault()).isTrue();
    }

    @Test
    void toResponse_nullType_mapsToNull() {
        Address a = address();
        a.setAddressType(null);

        assertThat(mapper.toResponse(a).getAddressType()).isNull();
    }

    @Test
    void toResponses_null_returnsEmptyList() {
        assertThat(mapper.toResponses(null)).isEmpty();
    }

    @Test
    void toResponses_mapsEachElement() {
        List<AddressResponse> out = mapper.toResponses(List.of(address(), address()));

        assertThat(out).hasSize(2);
    }
}
