package org.praskanth.service;

import org.praskanth.dto.AddressRequest;
import org.praskanth.dto.AddressResponse;

import java.util.List;

public interface AddressService {
    AddressResponse addAddress(String email, AddressRequest request);
    List<AddressResponse> getUserAddresses(String email);
    AddressResponse updateAddress(String email, Long addressId, AddressRequest request);
    void deleteAddress(String email, Long addressId);
    AddressResponse setDefaultAddress(String email, Long addressId);
}
