package org.praskanth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Seller business + compliance data. Used for registration, onboarding an existing customer,
 * and profile updates, so the rules are defined exactly once.
 */
public record SellerProfileRequest(
        @NotBlank(message = "GSTIN is required")
        @Pattern(regexp = ValidationPatterns.GSTIN, message = "Invalid GSTIN format")
        String gstin,

        @NotBlank(message = "PAN is required")
        @Pattern(regexp = ValidationPatterns.PAN, message = "Invalid PAN format")
        String pan,

        @NotNull(message = "Business details are required")
        @Valid
        Business business,

        @NotNull(message = "Bank details are required")
        @Valid
        Bank bank
) {
    public record Business(
            @NotBlank(message = "Store name is required") @Size(min = 3, max = 120)
            String businessName,

            @NotBlank(message = "Legal name is required") @Size(max = 200)
            String legalName,

            @NotBlank(message = "Business address is required") @Size(max = 500)
            String businessAddress,

            @NotBlank(message = "Business phone is required")
            @Pattern(regexp = ValidationPatterns.PHONE, message = "Invalid business phone number")
            String businessPhone,

            @NotBlank(message = "Business email is required") @Email @Size(max = 254)
            String businessEmail,

            @URL @Size(max = 500)
            String logo,

            @URL @Size(max = 500)
            String banner
    ) {}

    public record Bank(
            @NotBlank(message = "Account holder name is required") @Size(max = 100)
            String accountHolderName,

            @NotBlank(message = "Account number is required")
            @Pattern(regexp = ValidationPatterns.BANK_ACCOUNT, message = "Account number must be 9-18 digits")
            String accountNumber,

            @NotBlank(message = "IFSC code is required")
            @Pattern(regexp = ValidationPatterns.IFSC, message = "Invalid IFSC code")
            String ifscCode
    ) {
        @Override
        public String toString() {
            return "Bank[accountHolderName=" + accountHolderName + ", ifscCode=" + ifscCode + "]";
        }
    }
}
