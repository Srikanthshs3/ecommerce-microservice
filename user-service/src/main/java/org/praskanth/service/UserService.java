package org.praskanth.service;

import org.praskanth.dto.CustomerRegisterRequest;
import org.praskanth.dto.RoleUpdateRequest;
import org.praskanth.dto.UserResponse;
import org.praskanth.dto.UserUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Customer registration, self-service profile, and admin user management. Seller flows are in SellerService. */
public interface UserService {

    // --- customer registration & self service ---
    UserResponse registerCustomer(CustomerRegisterRequest request);

    UserResponse getCurrentUser(Long userId);

    UserResponse updateProfile(Long userId, UserUpdateRequest request);

    // --- admin / support ---
    UserResponse getUserById(Long userId);

    Page<UserResponse> getAllUsers(Pageable pageable);

    UserResponse updateUserRoles(Long userId, RoleUpdateRequest request, Long actingAdminId);

    void deleteUser(Long userId, Long actingAdminId);

    void unlockUser(Long userId);
}
