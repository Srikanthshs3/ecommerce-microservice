package org.praskanth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterCompleteRequest {

    @NotBlank
    @Email
    private String email;

    @NotBlank(message = "Verification token from the /register/verify-otp step is required")
    private String verificationToken;

    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    @NotBlank
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String password;
}
