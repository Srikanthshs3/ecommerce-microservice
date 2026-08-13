package org.praskanth.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.AddressResponse;
import org.praskanth.dto.RoleUpdateRequest;
import org.praskanth.dto.UserResponse;
import org.praskanth.dto.UserUpdateRequest;
import org.praskanth.entity.Address;
import org.praskanth.entity.Role;
import org.praskanth.entity.SellerProfile;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.repository.AddressRepo;
import org.praskanth.repository.RoleRepo;
import org.praskanth.repository.SellerProfileRepo;
import org.praskanth.repository.UserRepo;
import org.praskanth.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepo userRepo;
    private final RoleRepo roleRepo;
    private final SellerProfileRepo sellerProfileRepo;
    private final AddressRepo addressRepo;


    @Override
    @Transactional(readOnly = true)
    public UserResponse getCurrentUserProfile(String email) {
        User user = userRepo.findByEmail(email).orElseThrow(() ->
                new NotFoundException("User with email " + email + " not found"));
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateProfile(String email, UserUpdateRequest request) {
        User user = userRepo.findByEmail(email).orElseThrow(() ->
                new NotFoundException("User with email " + email + " not found"));
        user.setName(request.getName());
        if (request.getStoreName()!= null && !request.getStoreName().isBlank()){
            SellerProfile sellerProfile = user.getSellerProfile();
            sellerProfile.setStoreName(request.getStoreName());
        }
        User updatedUser = userRepo.save(user);
        log.info("User profile updated for email: {}", email);
        return mapToUserResponse(updatedUser);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(Long userId) {
        User user = userRepo.findById(userId).orElseThrow(() ->
                new NotFoundException("User with ID " + userId + " not found"));
        return mapToUserResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getAllUsers(Pageable pageable) {
        return userRepo.findAll(pageable).map(this::mapToUserResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> getSellersByKycStatus(SellerProfile.KycStatus kycStatus, Pageable pageable) {
        return sellerProfileRepo.findByKycStatus(kycStatus, pageable)
                .map(sellerProfile -> mapToUserResponse(sellerProfile.getUser()));
    }

    @Override
    @Transactional
    public UserResponse updateKycStatus(Long userId, SellerProfile.KycStatus kycStatus) {
        User user = userRepo.findById(userId).orElseThrow(() ->
                new NotFoundException("User with ID " + userId + " not found"));

        SellerProfile sellerProfile = user.getSellerProfile();
        if (sellerProfile == null) {
            throw new BadRequestException("User with ID " + userId + " does not have a seller profile");
        }

        sellerProfile.setKycStatus(kycStatus);
        sellerProfileRepo.save(sellerProfile);
        log.info("KYC status updated for user ID: {} to {}", userId, kycStatus);
        return mapToUserResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUserRole(Long userId, RoleUpdateRequest request) {
        User user = userRepo.findById(userId).orElseThrow(() ->
                new NotFoundException("User with ID " + userId + " not found"));

        Set<Role> updatedRoles = request.getRoles().stream()
                .map(roleName -> roleRepo.findByName(roleName)
                        .orElseThrow(() -> new BadRequestException("Role " + roleName + " not found")))
                .collect(Collectors.toSet());

        user.setRole(updatedRoles);
        User savedUser = userRepo.save(user);
        log.info("Roles updated for user ID: {} to {}", userId, updatedRoles);

        return mapToUserResponse(savedUser);
    }

    @Override
    @Transactional
    public void deleteUser(Long userId) {
        if (!userRepo.existsById(userId)) {
            throw new NotFoundException("User with ID " + userId + " not found");
        }
        userRepo.deleteById(userId);
        log.info("User with ID: {} has been deleted", userId);

    }

    private UserResponse mapToUserResponse(User user) {
        String kycStatus = user.getSellerProfile() != null
                ? user.getSellerProfile().getKycStatus().name() : null;

        String storeName = user.getSellerProfile() != null
                ? user.getSellerProfile().getStoreName() : null;

        Set<String> roles = user.getRole().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet());

        List<AddressResponse> userAddresses = null;
        if (user.getCustomerProfile() != null && user.getCustomerProfile().getAddresses() != null) {
            userAddresses = user.getCustomerProfile().getAddresses().stream()
                    .map(this::mapToAddressResponse)
                    .collect(Collectors.toList());
        }

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(roles)
                .kycStatus(kycStatus)
                .storeName(storeName)
                .createdAt(user.getCreatedAt())
                .addresses(userAddresses)
                .build();
    }

    private AddressResponse mapToAddressResponse(Address address) {
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
