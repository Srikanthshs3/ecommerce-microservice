package org.praskanth.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.*;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.service.AuthService;
import org.praskanth.service.UserService;
import org.praskanth.util.CookieUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication for all actors. Customer registration lives here (it is the default sign-up);
 * seller registration is POST /api/v1/sellers/register and reuses the same two OTP endpoints.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final CookieUtils cookieUtils;

    // ----- registration: step 1 & 2 are shared by customers and sellers -----

    @PostMapping("/register/initiate")
    public ResponseEntity<ApiResponse<Void>> initiate(@Valid @RequestBody RegisterInitiateRequest request) {
        authService.initiateRegistration(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("OTP sent to your email", null));
    }

    @PostMapping("/register/verify-otp")
    public ResponseEntity<ApiResponse<OtpVerifyResponse>> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        OtpVerifyResponse response = authService.verifyRegistrationOtp(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success("Email verified", response));
    }

    // ----- registration: step 3 for customers -----

    @PostMapping("/register/customer")
    public ResponseEntity<ApiResponse<UserResponse>> registerCustomer(@Valid @RequestBody CustomerRegisterRequest request) {
        UserResponse response = userService.registerCustomer(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Registration complete", response));
    }

    // ----- sessions -----

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthService.LoginResult result = authService.login(request);
        ResponseCookie cookie = cookieUtils.createRefreshTokenCookie(result.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("Login Successful", result.body()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refresh(
            @CookieValue(name = CookieUtils.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
        AuthService.RefreshResult result = authService.refresh(refreshToken);
        ResponseCookie cookie = cookieUtils.createRefreshTokenCookie(result.refreshToken());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(ApiResponse.success("Token refreshed successfully", result.body()));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @CookieValue(name = CookieUtils.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {
        authService.logout(refreshToken);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteRefreshTokenCookie().toString())
                .body(ApiResponse.success("Logged out successfully"));
    }

    /** Sign out of every device. */
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<String>> logoutAll(@AuthenticationPrincipal GatewayUser me) {
        authService.logoutAllSessions(me.id());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteRefreshTokenCookie().toString())
                .body(ApiResponse.success("Logged out from all devices"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal GatewayUser me) {
        return ResponseEntity.ok(ApiResponse.success(userService.getCurrentUser(me.id())));
    }

    // ----- password management -----

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("If an account exists, a password reset link has been sent to the email."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. You can now log in."));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(@AuthenticationPrincipal GatewayUser me,
                                                              @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(me.id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookieUtils.deleteRefreshTokenCookie().toString())
                .body(ApiResponse.success("Password changed. Please sign in again on your other devices."));
    }
}
