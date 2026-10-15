package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.AddressRequest;
import org.praskanth.dto.AddressResponse;
import org.praskanth.dto.ApiResponse;
import org.praskanth.service.AddressService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/address")
public class AddressController {

    private final AddressService addressService;

    @PostMapping
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(
            @Valid @RequestBody AddressRequest request,
            Principal principal
    ) {
        // principal.getName() securely returns the email extracted from the JWT
        AddressResponse response = addressService.addAddress(principal.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Address added successfully", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AddressResponse>>> getUserAddresses(
            Principal principal
    ) {
        List<AddressResponse> responses = addressService.getUserAddresses(principal.getName());
        return ResponseEntity.ok(ApiResponse.success("Addresses fetched successfully", responses));
    }

    @PutMapping("/{addressId}")
    public ResponseEntity<ApiResponse<AddressResponse>> updateAddress(
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request,
            Principal principal
    ) {
        AddressResponse response = addressService.updateAddress(principal.getName(), addressId, request);
        return ResponseEntity.ok(ApiResponse.success("Address updated successfully", response));
    }

    @DeleteMapping("/{addressId}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable Long addressId,
            Principal principal
    ) {
        addressService.deleteAddress(principal.getName(), addressId);
        return ResponseEntity.ok(ApiResponse.success("Address deleted successfully", null));
    }

    @PatchMapping("/{addressId}/default")
    public ResponseEntity<ApiResponse<AddressResponse>> setDefaultAddress(
            @PathVariable Long addressId,
            Principal principal
    ) {
        AddressResponse response = addressService.setDefaultAddress(principal.getName(), addressId);
        return ResponseEntity.ok(ApiResponse.success("Default address updated successfully", response));
    }
}
