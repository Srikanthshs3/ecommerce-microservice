package org.praskanth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.AddressRequest;
import org.praskanth.dto.AddressResponse;
import org.praskanth.entity.Address;
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
        User user = getUser(email);

        if (request.isDefault()) {
            clearPreviousDefault(user.getId(), request.getAddressType());
        }

        Address address = Address.builder()
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

        // Dynamically link the address to the correct profile
        if (user.getSellerProfile() != null) {
            address.setSellerProfile(user.getSellerProfile());
        } else if (user.getCustomerProfile() != null) {
            address.setCustomerProfile(user.getCustomerProfile());
        } else {
            throw new BadRequestException("User does not have an active profile to attach an address.");
        }

        Address savedAddress = addressRepo.save(address);
        log.info("Address added for user with email: {}", email);
        return mapToResponse(savedAddress);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AddressResponse> getUserAddresses(String email) {
        User user = getUser(email);
        return addressRepo.findAllByUserId(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional
    public AddressResponse updateAddress(String email, Long addressId, AddressRequest request) {
        User user = getUser(email);
        Address address = getAddressSecurityCheck(addressId, user.getId());

        if (request.isDefault() && !address.isDefault()) {
            clearPreviousDefault(user.getId(), request.getAddressType());
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
        User user = getUser(email);
        Address address = getAddressSecurityCheck(addressId, user.getId());

        addressRepo.delete(address);
        log.info("Address with ID {} deleted for user with email: {}", addressId, email);
    }

    @Override
    @Transactional
    public AddressResponse setDefaultAddress(String email, Long addressId) {
        User user = getUser(email);
        Address address = getAddressSecurityCheck(addressId, user.getId());

        clearPreviousDefault(user.getId(), address.getAddressType());
        address.setDefault(true);

        Address updatedAddress = addressRepo.save(address);
        log.info("Address with ID {} set as default for user with email: {}", addressId, email);
        return mapToResponse(updatedAddress);
    }

    // --- Helper methods ---

    private User getUser(String email){
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("We couldn't find an account with that email."));
    }

    private Address getAddressSecurityCheck(Long addressId, Long userId) {
        return addressRepo.findByAddressIdAndUserId(addressId, userId)
                .orElseThrow(() -> new NotFoundException("We couldn't find that address."));
    }

    private void clearPreviousDefault(Long userId, Address.AddressType type) {
        List<Address> addresses = addressRepo.findAllByUserId(userId);
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