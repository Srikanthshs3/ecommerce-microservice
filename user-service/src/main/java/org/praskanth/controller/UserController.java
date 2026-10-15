package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.RoleUpdateRequest;
import org.praskanth.dto.UserResponse;
import org.praskanth.dto.UserUpdateRequest;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.UserService;
import org.praskanth.util.PageRequests;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

/**
 * Account profile for the signed-in user plus admin/support user management.
 * Seller-specific endpoints (KYC review, seller lists) moved to SellerController.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/user")
public class UserController {

    private static final Set<String> USER_SORT_FIELDS = Set.of("id", "email", "name", "createdAt");

    private final UserService userService;

    // ---------- signed-in user ----------

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserResponse>> getMyProfile(@AuthenticationPrincipal GatewayUser me) {
        return ResponseEntity.ok(ApiResponse.success("User profile fetched successfully",
                userService.getCurrentUser(me.id())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(@AuthenticationPrincipal GatewayUser me,
                                                                     @Valid @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User profile updated successfully",
                userService.updateProfile(me.id(), request)));
    }

    // ---------- ADMIN / SUPPORT ----------

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'SUPPORT_AGENT')")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("User profile fetched successfully", userService.getUserById(id)));
    }

    // ---------- ADMIN only ----------

    @GetMapping("/get-all")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Page<UserResponse>>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        var pageable = PageRequests.of(page, size, sortBy, direction, USER_SORT_FIELDS, "id");
        return ResponseEntity.ok(ApiResponse.success(userService.getAllUsers(pageable)));
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponse>> updateUserRoles(@PathVariable Long id,
                                                                     @Valid @RequestBody RoleUpdateRequest request,
                                                                     @AuthenticationPrincipal GatewayUser admin) {
        return ResponseEntity.ok(ApiResponse.success("User roles updated successfully",
                userService.updateUserRoles(id, request, admin.id())));
    }

    @PutMapping("/{id}/unlock")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<String>> unlockUser(@PathVariable Long id) {
        userService.unlockUser(id);
        return ResponseEntity.ok(ApiResponse.success("User account unlocked successfully."));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable Long id,
                                                        @AuthenticationPrincipal GatewayUser admin) {
        userService.deleteUser(id, admin.id());
        return ResponseEntity.ok(ApiResponse.success("User deleted successfully", null));
    }
}
