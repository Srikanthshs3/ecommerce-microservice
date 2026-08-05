package org.praskanth.controller;


import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.*;
import org.praskanth.entity.User;
import org.praskanth.exception.NotFoundException;
import org.praskanth.exception.UnauthorizedException;
import org.praskanth.repository.UserRepo;
import org.praskanth.security.JwtUtils;
import org.praskanth.service.RegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registrationService;
    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

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
        User user = userRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String token = jwtUtils.generateToken(user);
        LoginResponse response = LoginResponse.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
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
                .role(user.getRole().name())
                .createdAt(user.getCreatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
