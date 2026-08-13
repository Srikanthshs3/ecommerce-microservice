package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.AddressRequest;
import org.praskanth.dto.AddressResponse;
import org.praskanth.dto.ApiResponse;
import org.praskanth.exception.UnauthorizedException;
import org.praskanth.service.AddressService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/address")
@PreAuthorize("hasAuthority('CUSTOMER')")
public class AddressController {
    private final AddressService addressService;

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(Principal principal, @Valid @RequestBody AddressRequest request) {
        validatePrincipal(principal);
        AddressResponse addressResponse = addressService.addAddress(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Address created successfully", addressResponse));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getMyAddresses(Principal principal) {
        validatePrincipal(principal);
        List<AddressResponse> responses = addressService.getUserAddresses(principal.getName());
        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            Principal principal,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request) {
        validatePrincipal(principal);
        AddressResponse response = addressService.updateAddress(principal.getName(), addressId, request);
        return ResponseEntity.ok(ApiResponse.success("Address updated successfully", response));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<ApiResponse<String>> deleteAddress(
            Principal principal,
            @PathVariable Long addressId) {
        validatePrincipal(principal);
        addressService.deleteAddress(principal.getName(), addressId);
        return ResponseEntity.ok(ApiResponse.success("Address deleted successfully"));
    }

    @PutMapping("/{addressId}/default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            Principal principal,
            @PathVariable Long addressId) {
        validatePrincipal(principal);
        AddressResponse response = addressService.setDefaultAddress(principal.getName(), addressId);
        return ResponseEntity.ok(ApiResponse.success("Default address updated successfully", response));
    }


    private void validatePrincipal(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new UnauthorizedException("Authentication token missing or invalid");
        }
    }
}
