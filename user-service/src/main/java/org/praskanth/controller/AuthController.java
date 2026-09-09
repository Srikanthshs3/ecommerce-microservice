package org.praskanth.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.*;
import org.praskanth.entity.User;
import org.praskanth.exception.NotFoundException;
import org.praskanth.exception.UnauthorizedException;
import org.praskanth.repository.UserRepo;
import org.praskanth.service.RefreshTokenService;
import org.praskanth.service.RegistrationService;
import org.praskanth.util.CookieUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registrationService;
    private final UserRepo userRepo;
    private final RefreshTokenService refreshTokenService;
    private final CookieUtils cookieUtils;

    @PostMapping("/register/initiate")
    public ResponseEntity<ApiResponse<Void>> initiate(@Valid @RequestBody RegisterInitiateRequest request) {
        registrationService.initiate(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("OTP sent to your email", null));
    }

    @PostMapping("/register/verify-otp")
    public ResponseEntity<ApiResponse<OtpVerifyResponse>> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        OtpVerifyResponse response = registrationService.verifyOtp(request.getEmail(), request.getOtp());
        return ResponseEntity.ok(ApiResponse.success("Email verified", response));
    }

    @PostMapping("/register/complete")
    public ResponseEntity<ApiResponse<UserResponse>> complete(@Valid @RequestBody RegisterCompleteRequest request) {
        UserResponse response = registrationService.complete(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Registration complete", response));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {

        Map<String, Object> result = registrationService.login(request);
        LoginResponse response = (LoginResponse) result.get("response");
        String refreshToken = (String) result.get("refreshToken");

        ResponseCookie responseCookie = cookieUtils.createRefreshTokenCookie(refreshToken, refreshTokenService.getRefreshTokenDuration());

        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, responseCookie.toString())
                .body(ApiResponse.success("Login Successful", response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<RefreshTokenResponse>> refreshToken(
            @CookieValue(name = CookieUtils.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {

        Map<String, Object> result = registrationService.refreshAccessToken(refreshToken);
        RefreshTokenResponse response = (RefreshTokenResponse) result.get("response");
        String newRefreshToken = (String) result.get("newRefreshToken");

        ResponseCookie rotatedCookie = cookieUtils.createRefreshTokenCookie(
                newRefreshToken,
                refreshTokenService.getRefreshTokenDuration()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, rotatedCookie.toString())
                .body(ApiResponse.success("Token refreshed successfully", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @CookieValue(name = CookieUtils.REFRESH_TOKEN_COOKIE_NAME, required = false) String refreshToken) {

        registrationService.logout(refreshToken);
        ResponseCookie clearCookie = cookieUtils.deleteRefreshTokenCookie();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie.toString())
                .body(ApiResponse.success("Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(java.security.Principal principal) {
        if (principal == null) {
            throw new UnauthorizedException("Not authenticated");
        }
        User user = userRepo.findByEmail(principal.getName())
                .orElseThrow(() -> new NotFoundException("User not found"));

        UserResponse response = UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().stream().map(role -> role.getName().name()).collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        registrationService.generatePasswordResetToken(request.getEmail());
        return ResponseEntity.ok(ApiResponse.success("If an account exists, a password reset link has been sent to the email."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        registrationService.resetPassword(request.getToken(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success("Password reset successfully. You can now log in."));
    }
}
