package org.praskanth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class LoginRequest {

    @NotBlank(message = "email is required")
    @Email
    private String email;
    @NotBlank(message = "password is required")
    @ToString.Exclude
    private String password;

    /** Token from the "I'm not a robot" check; only needed after repeated failed logins. */
    @jakarta.validation.constraints.Size(max = 4096)
    @ToString.Exclude
    private String captchaToken;
}
