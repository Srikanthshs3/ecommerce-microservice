package org.praskanth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Seller registration = login credentials + verified email token + seller profile + agreement acceptance. */
public record SellerRegisterRequest(
        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Size(max = 254)
        String email,

        @NotBlank(message = "Verification token is required")
        String verificationToken,

        @NotBlank(message = "Password is required")
        @Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
        String password,

        @NotBlank(message = "Name is required")
        @Size(max = 100)
        String name,

        @NotBlank(message = "Phone number is required for sellers")
        @Pattern(regexp = ValidationPatterns.PHONE, message = "Invalid phone number")
        String phoneNumber,

        @NotNull(message = "Seller profile is required")
        @Valid
        SellerProfileRequest profile,

        @AssertTrue(message = "You must accept the seller agreement")
        boolean acceptTerms
) {
    @Override
    public String toString() {
        return "SellerRegisterRequest[email=" + email + "]";
    }
}
