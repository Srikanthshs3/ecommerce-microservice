package org.praskanth.service;

import org.praskanth.dto.RoleUpdateRequest;
import org.praskanth.dto.UserResponse;
import org.praskanth.dto.UserUpdateRequest;
import org.praskanth.entity.SellerProfile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UserService {

    UserResponse getCurrentUserProfile(String email);

    UserResponse updateProfile(String email, UserUpdateRequest updateRequest);

    UserResponse getUserById(Long userId);

    Page<UserResponse> getAllUsers(Pageable pageable);

    Page<UserResponse> getSellersByKycStatus(SellerProfile.KycStatus kycStatus, Pageable pageable);

    UserResponse updateKycStatus(Long userId, SellerProfile.KycStatus kycStatus);

    UserResponse updateUserRole(Long userId, RoleUpdateRequest request);

    void deleteUser(Long userId);
}
