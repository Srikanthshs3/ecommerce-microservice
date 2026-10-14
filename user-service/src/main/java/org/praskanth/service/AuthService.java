package org.praskanth.service;

import org.praskanth.dto.LoginRequest;
import org.praskanth.dto.LoginResponse;
import org.praskanth.dto.OtpVerifyResponse;
import org.praskanth.dto.RefreshTokenResponse;

/**
 * Credentials and sessions for every actor: email verification, login, token refresh, logout and
 * password management. Account creation lives in UserService (customers) and SellerService (sellers).
 */
public interface AuthService {

    void initiateRegistration(String email);

    OtpVerifyResponse verifyRegistrationOtp(String email, String otp);

    LoginResult login(LoginRequest request);

    RefreshResult refresh(String refreshToken);

    void logout(String refreshToken);

    void logoutAllSessions(Long userId);

    void requestPasswordReset(String email);

    void resetPassword(String token, String newPassword);

    void changePassword(Long userId, String currentPassword, String newPassword);

    /** Body for the JSON response + raw refresh token for the HttpOnly cookie. */
    record LoginResult(LoginResponse body, String refreshToken) {}

    record RefreshResult(RefreshTokenResponse body, String refreshToken) {}
}
