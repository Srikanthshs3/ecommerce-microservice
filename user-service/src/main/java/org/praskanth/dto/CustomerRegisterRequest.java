package org.praskanth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Step 3 of customer registration (after /register/initiate and /register/verify-otp). */
public record CustomerRegisterRequest(
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

        @Pattern(regexp = ValidationPatterns.PHONE, message = "Invalid phone number")
        String phoneNumber
) {
    @Override
    public String toString() {
        return "CustomerRegisterRequest[email=" + email + "]"; // never print secrets
    }
}
