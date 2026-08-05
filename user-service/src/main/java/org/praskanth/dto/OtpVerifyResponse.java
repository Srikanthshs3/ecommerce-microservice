package org.praskanth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Short-lived token proving the email was verified; required by the /register/complete step. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class OtpVerifyResponse {
    private String verificationToken;
    private long expiresInSeconds;
}
