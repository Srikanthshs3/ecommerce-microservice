package org.praskanth.mapper;

import org.praskanth.dto.AddressResponse;
import org.praskanth.entity.Address;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AddressMapper {

    public AddressResponse toResponse(Address a) {
        return AddressResponse.builder()
                .id(a.getId())
                .recipientName(a.getRecipientName())
                .streetLine1(a.getStreetLine1())
                .streetLine2(a.getStreetLine2())
                .city(a.getCity())
                .state(a.getState())
                .postalCode(a.getPostalCode())
                .country(a.getCountry())
                .addressType(a.getAddressType() != null ? a.getAddressType().name() : null)
                .isDefault(a.isDefault())
                .build();
    }

    public List<AddressResponse> toResponses(List<Address> addresses) {
        return addresses == null ? List.of() : addresses.stream().map(this::toResponse).toList();
    }
}
