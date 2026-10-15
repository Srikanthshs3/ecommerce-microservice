package org.praskanth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Self-service update of the basic account profile. Seller business data uses SellerProfileRequest. */
public record UserUpdateRequest(
        @NotBlank(message = "Name cannot be empty")
        @Size(max = 100)
        String name,

        @Pattern(regexp = ValidationPatterns.PHONE, message = "Invalid phone number")
        String phoneNumber
) {}
