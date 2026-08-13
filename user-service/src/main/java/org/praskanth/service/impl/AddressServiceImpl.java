package org.praskanth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.AddressRequest;
import org.praskanth.dto.AddressResponse;
import org.praskanth.entity.Address;
import org.praskanth.entity.CustomerProfile;
import org.praskanth.entity.User;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.repository.AddressRepo;
import org.praskanth.repository.UserRepo;
import org.praskanth.service.AddressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AddressServiceImpl implements AddressService {

    private final AddressRepo addressRepo;
    private final UserRepo userRepo;
    @Override
    @Transactional
    public AddressResponse addAddress(String email, AddressRequest request) {
        CustomerProfile customerProfile = getCustomerProfile(email);

        if (request.isDefault()) {
            clearPreviousDefault(customerProfile.getUserId(), request.getAddressType());
        }
        Address address = Address.builder()
                .customerProfile(customerProfile)
                .recipientName(request.getRecipientName())
                .streetLine1(request.getStreetLine1())
                .streetLine2(request.getStreetLine2())
                .city(request.getCity())
                .state(request.getState())
                .postalCode(request.getPostalCode())
                .country(request.getCountry())
                .addressType(request.getAddressType())
                .isDefault(request.isDefault())
                .build();
        Address savedAddress = addressRepo.save(address);
        log.info("Address added for user with email: {}", email);
        return mapToResponse(savedAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getUserAddresses(String email) {
        CustomerProfile customerProfile = getCustomerProfile(email);
        return addressRepo.findByCustomerProfileUserId(customerProfile.getUserId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public AddressResponse updateAddress(String email, Long addressId, AddressRequest request) {
        CustomerProfile customerProfile = getCustomerProfile(email);
        Address address = getAddressSecurityCheck(addressId, customerProfile.getUserId());

        if (request.isDefault() && !address.isDefault()) {
            clearPreviousDefault(customerProfile.getUserId(), request.getAddressType());
        }

        address.setRecipientName(request.getRecipientName());
        address.setStreetLine1(request.getStreetLine1());
        address.setStreetLine2(request.getStreetLine2());
        address.setCity(request.getCity());
        address.setState(request.getState());
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry());
        address.setAddressType(request.getAddressType());
        address.setDefault(request.isDefault());

        Address updatedAddress = addressRepo.save(address);
        log.info("Address with ID {} updated for user with email: {}", addressId, email);
        return mapToResponse(updatedAddress);
    }

    @Override
    @Transactional
    public void deleteAddress(String email, Long addressId) {
        CustomerProfile customerProfile = getCustomerProfile(email);
        Address address = getAddressSecurityCheck(addressId, customerProfile.getUserId());

        addressRepo.delete(address);
        log.info("Address with ID {} deleted for user with email: {}", addressId, email);
    }

    @Override
    @Transactional
    public AddressResponse setDefaultAddress(String email, Long addressId) {
        CustomerProfile customerProfile = getCustomerProfile(email);
        Address address = getAddressSecurityCheck(addressId, customerProfile.getUserId());

        clearPreviousDefault(customerProfile.getUserId(), address.getAddressType());
        address.setDefault(true);
        Address updatedAddress = addressRepo.save(address);
        log.info("Address with ID {} set as default for user with email: {}", addressId, email);
        return mapToResponse(updatedAddress);
    }

    // Helper methods

    private CustomerProfile getCustomerProfile(String email){
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User with email " + email + " not found"));
        if (user.getCustomerProfile() == null) {
            throw new BadRequestException("Only registered customers can manage addresses. User with email " + email + " is not a customer.");
        }
        return user.getCustomerProfile();
    }

    private Address getAddressSecurityCheck(Long addressId, Long customerId) {
        return addressRepo.findByIdAndCustomerProfileUserId(addressId, customerId)
                .orElseThrow(() -> new NotFoundException("Address with ID " + addressId + " not found for the current user or you do not have permission to modify."));
    }

    private void clearPreviousDefault(Long customerId, Address.AddressType type) {
        List<Address> addresses = addressRepo.findByCustomerProfileUserId(customerId);
        for (Address address : addresses) {
            if (address.getAddressType() == type && address.isDefault()) {
                address.setDefault(false);
                addressRepo.save(address);
            }
        }
    }

    private AddressResponse mapToResponse(Address address) {
        return AddressResponse.builder()
                .id(address.getId())
                .recipientName(address.getRecipientName())
                .streetLine1(address.getStreetLine1())
                .streetLine2(address.getStreetLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .addressType(address.getAddressType().name())
                .isDefault(address.isDefault())
                .build();
    }



}
