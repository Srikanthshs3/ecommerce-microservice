package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.*;
import org.praskanth.entity.SellerProfile;
import org.praskanth.entity.User;
import org.praskanth.exception.NotFoundException;
import org.praskanth.exception.UnauthorizedException;
import org.praskanth.repository.UserRepo;
import org.praskanth.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user")
public class UserController {

    private final UserService userService;
    private final UserRepo userRepo;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> getUserProfile(Principal principal){
        validatePrincipal(principal);
        UserResponse userResponse = userService.getCurrentUserProfile(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("User profile fetched successfully", userResponse));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(Principal principal, @Valid @RequestBody UserUpdateRequest request){
        validatePrincipal(principal);
        UserResponse userResponse = userService.updateProfile(principal.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("User profile updated successfully", userResponse));
    }

    // ADMIN && SUPPORT Endpoints

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPPORT_AGENT')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserProfileById(@PathVariable Long id){
        UserResponse userResponse = userService.getUserById(id);
        return ResponseEntity.ok(ApiResponse.success("User profile fetched successfully", userResponse));
    }

    @GetMapping("/get-all")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(@RequestParam(defaultValue = "0") int page,
                                                                 @RequestParam(defaultValue = "10") int size,
                                                                 @RequestParam(defaultValue = "id") String sortBy){
        Page<UserResponse> usersPage = userService.getAllUsers(PageRequest.of(page, size, Sort.by(sortBy).descending()));
        return ResponseEntity.ok(ApiResponse.success(usersPage));
    }

    @GetMapping("/sellers/kyc")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPPORT_AGENT')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getSellersByKycStatus(@RequestParam SellerProfile.KycStatus kycStatus,
                                                                                 @RequestParam(defaultValue = "0") int page,
                                                                                 @RequestParam(defaultValue = "10") int size,
                                                                                 @RequestParam(defaultValue = "userId") String sortBy){
        Page<UserResponse> sellersPage = userService.getSellersByKycStatus(kycStatus, PageRequest.of(page, size, Sort.by(sortBy).descending()));
        return ResponseEntity.ok(ApiResponse.success(sellersPage));
    }

    // ADMIN Only Endpoints

    @PutMapping("/{id}/kyc-status")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateSellerKycStatus(@PathVariable Long id,
                                                                           @RequestParam SellerProfile.KycStatus kycStatus){
        UserResponse userResponse = userService.updateKycStatus(id, kycStatus);
        return ResponseEntity.ok(ApiResponse.success("Seller KYC status updated successfully", userResponse));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRoles(@PathVariable Long id,
                                                                      @Valid @RequestBody RoleUpdateRequest role){
        UserResponse userResponse = userService.updateUserRole(id, role);
        return ResponseEntity.ok(ApiResponse.success("User roles updated successfully", userResponse));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id){
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }

    @PutMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<String>> unlockUserAccount(@PathVariable Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));

        user.setAccountNonLocked(true);
        user.setFailedAttemptCount(0);
        userRepo.save(user);

        return ResponseEntity.ok(ApiResponse.success("User account unlocked successfully."));
    }

    @GetMapping("/allSellers")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Page<SellerResponse>>> getAllSellers(@RequestParam(defaultValue = "0") int page,
                                                                           @RequestParam(defaultValue = "10") int size,
                                                                           @RequestParam(defaultValue = "id") String sortBy){
        Page<SellerResponse> allSellers = userService.getAllSellers(PageRequest.of(page, size, Sort.by(sortBy).descending()));
        return ResponseEntity.ok(ApiResponse.success(allSellers));
    }


    private void validatePrincipal(Principal principal){
        if (principal == null || principal.getName() == null) {
            throw new UnauthorizedException("Authentication token is missing or invalid");
        }
    }


}
