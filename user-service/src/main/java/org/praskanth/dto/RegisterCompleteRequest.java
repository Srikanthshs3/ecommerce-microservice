package org.praskanth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.praskanth.enums.UserRole;

@Data
public class RegisterCompleteRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Verification token is required")
    private String verificationToken;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Name is required")
    private String name;

    private String phoneNumber;

    @NotNull(message = "User role is required")
    private UserRole role; // CUSTOMER or SELLER

    // Required only when role == ROLE_SELLER
    private BankDetails bankDetails = new BankDetails();

    private BusinessDetails  businessDetails = new BusinessDetails();

    private String GSTIN;
}
