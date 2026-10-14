package org.praskanth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "Token is required")
    @ToString.Exclude
    private String token;

    @NotBlank(message = "New password is required")
    @jakarta.validation.constraints.Pattern(regexp = ValidationPatterns.PASSWORD, message = ValidationPatterns.PASSWORD_MESSAGE)
    @ToString.Exclude
    private String newPassword;
}
