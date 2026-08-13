package org.praskanth.service;

import jakarta.ws.rs.ForbiddenException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.*;
import org.praskanth.entity.CustomerProfile;
import org.praskanth.entity.Role;
import org.praskanth.entity.SellerProfile;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.UnauthorizedException;
import org.praskanth.kafka.UserEventProducer;
import org.praskanth.repository.RoleRepo;
import org.praskanth.repository.UserRepo;
import org.praskanth.security.JwtUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationService {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration VERIFIED_TOKEN_TTL = Duration.ofMinutes(15);
    private static final int MAX_OTP_ATTEMPTS = 5;

    private static final String OTP_KEY_PREFIX = "otp:register:";
    private static final String OTP_ATTEMPTS_PREFIX = "otp:register:attempts:";
    private static final String RESEND_LOCK_PREFIX = "otp:register:resend-lock:";
    private static final String VERIFIED_TOKEN_PREFIX = "otp:register:verified:";

    private final UserRepo userRepo;
    private final StringRedisTemplate redisTemplate;
    private final PasswordEncoder passwordEncoder;
    private final UserEventProducer userEventProducer;
    private final RoleRepo roleRepo;
    private final JwtUtils jwtUtils;

    private final SecureRandom secureRandom = new SecureRandom();

    public void initiate(String email) {
        if (userRepo.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }

        String resendLockKey = RESEND_LOCK_PREFIX + email;
        Boolean lockAcquired = redisTemplate.opsForValue()
                .setIfAbsent(resendLockKey, "1", RESEND_COOLDOWN);
        if (Boolean.FALSE.equals(lockAcquired)) {
            throw new BadRequestException("Please wait before requesting another OTP");
        }

        String otp = generateOtp();
        redisTemplate.opsForValue().set(OTP_KEY_PREFIX + email, otp, OTP_TTL);
        redisTemplate.delete(OTP_ATTEMPTS_PREFIX + email);
        log.info("Generated OTP {} for {}", otp, email);
        System.out.println("Generated OTP " + otp + " for " + email); // For demonstration purposes only; remove in production
        userEventProducer.publishSendOtpEmail(email, otp);

        log.info("Registration OTP issued for {}", email);
    }

    public OtpVerifyResponse verifyOtp(String email, String otp) {
        String otpKey = OTP_KEY_PREFIX + email;
        String attemptsKey = OTP_ATTEMPTS_PREFIX + email;

        String storedOtp = redisTemplate.opsForValue().get(otpKey);
        if (storedOtp == null) {
            throw new BadRequestException("OTP expired or was never requested. Please request a new one");
        }

        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        redisTemplate.expire(attemptsKey, OTP_TTL);
        if (attempts != null && attempts > MAX_OTP_ATTEMPTS) {
            redisTemplate.delete(otpKey);
            redisTemplate.delete(attemptsKey);
            throw new BadRequestException("Too many incorrect attempts. Please request a new OTP");
        }

        if (!storedOtp.equals(otp)) {
            throw new BadRequestException("Invalid OTP");
        }

        redisTemplate.delete(otpKey);
        redisTemplate.delete(attemptsKey);

        String verificationToken = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(VERIFIED_TOKEN_PREFIX + email, verificationToken, VERIFIED_TOKEN_TTL);

        log.info("Email verified for {}", email);
        return new OtpVerifyResponse(verificationToken, VERIFIED_TOKEN_TTL.toSeconds());
    }

    //@Transactional
    public UserResponse complete(RegisterCompleteRequest request) {
        String verifiedKey = VERIFIED_TOKEN_PREFIX + request.getEmail();
        String storedToken = redisTemplate.opsForValue().get(verifiedKey);

        if (storedToken == null || !storedToken.equals(request.getVerificationToken())) {
            throw new UnauthorizedException("Email verification expired or invalid. Please verify your email again");
        }

        // Re-check for the race where two /complete calls land concurrently for the same email.
        if (userRepo.existsByEmail(request.getEmail())) {
            throw new ConflictException("An account with this email already exists");
        }

        UserRole targetRole = request.getRole();
        /*if (targetRole == UserRole.ADMIN || targetRole == UserRole.SUPPORT_AGENT) {
            throw new BadRequestException("Cannot register as ADMIN or SUPPORT_AGENT through this endpoint");

        }*/

        Role dbRole = roleRepo.findByName(targetRole)
                .orElseThrow(() -> new BadRequestException("Role " + targetRole + " not found in the system"));

        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .password(passwordEncoder.encode(request.getPassword()))
                .phoneNumber(request.getPhoneNumber())
                .role(Set.of(dbRole))
                .emailVerified(true)
                .createdAt(java.time.Instant.now())
                .build();

        if (targetRole == UserRole.SELLER){
            if (request.getStoreName() == null || request.getStoreName().isBlank()) {
                throw new BadRequestException("Store name is required for SELLER registration");
            }
            SellerProfile sellerProfile = SellerProfile.builder()
                    .storeName(request.getStoreName())
                    .user(user)
                    .taxIdentifier(request.getTaxIdentifier())
                    .businessRegistrationNumber(request.getBusinessRegistrationNumber())
                    .kycStatus(SellerProfile.KycStatus.PENDING)
                    .build();
            user.setSellerProfile(sellerProfile);
        }else {
            CustomerProfile customerProfile = CustomerProfile.builder()
                    .user(user)
                    .fullName(request.getName())
                    .build();
            user.setCustomerProfile(customerProfile);
        }

        User saved = userRepo.save(user);
        redisTemplate.delete(verifiedKey);

        userEventProducer.publishUserRegistered(saved);
        log.info("User registered as {} with email {}", targetRole, saved.getEmail());

        String kycStatusStr = saved.getSellerProfile() != null
                ? saved.getSellerProfile().getKycStatus().name() : null;

        String storedNameStr = saved.getSellerProfile() != null
                ? saved.getSellerProfile().getStoreName() : saved.getCustomerProfile().getFullName();

        return UserResponse.builder()
                .id(saved.getId())
                .email(saved.getEmail())
                .name(saved.getName())
                .role(saved.getRole().stream().map(role -> role.getName().name()).collect(Collectors.toSet()))
                .kycStatus(kycStatusStr)
                .storeName(storedNameStr)
                .createdAt(saved.getCreatedAt())
                .build();
    }

    @Transactional(noRollbackFor = {UnauthorizedException.class, ForbiddenException.class})
    public LoginResponse login(LoginRequest request) {
        User user = userRepo.findByEmail(request.getEmail())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!user.isAccountNonLocked()) {
            throw new ForbiddenException("Account is locked due to multiple failed attempts. Please reset your password or contact support.");
        }

        /*if (user.getFailedAttemptCount() >= 3) {
            if (request.getCaptchaToken() == null || request.getCaptchaToken().isBlank()) {
                // Frontend should catch this specific message and render the Google reCAPTCHA widget
                throw new UnauthorizedException("REQUIRE_CAPTCHA: Please complete the CAPTCHA to continue.");
            }

            // TODO: In a real app, verify the token with Google's API here
            // boolean isValid = captchaService.verify(request.getCaptchaToken());
            // if (!isValid) throw new UnauthorizedException("Invalid CAPTCHA");
        }*/

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {

            int newFailures = user.getFailedAttemptCount() + 1;
            user.setFailedAttemptCount(newFailures);
            if (newFailures >= 5) {
                user.setAccountNonLocked(false);
                userRepo.save(user);
                log.warn("User account {} locked due to too many failed login attempts", user.getEmail());
                throw new ForbiddenException("Account is locked due to multiple failed attempts. Please reset your password or contact support.");
            }
            userRepo.save(user);
            if (newFailures >= 3) {
                // Frontend should catch this specific message and render the Google reCAPTCHA widget
                log.warn("User {} has failed login attempts: {}. CAPTCHA required.", user.getEmail(), newFailures);
                //throw new UnauthorizedException("REQUIRE_CAPTCHA: Please complete the CAPTCHA to continue.");
            }
            throw new UnauthorizedException("Invalid email or password. Failed attempts: " + newFailures+ " of 5.");
        }

        if (user.getFailedAttemptCount() > 0) {
            user.setFailedAttemptCount(0); // Reset counter to 0!
            userRepo.save(user);
        }

        String token = jwtUtils.generateToken(user);
        return LoginResponse.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().stream().map(role -> role.getName().name()).findFirst().orElse("UNKNOWN"))
                .build();
    }

    @Transactional
    public void generatePasswordResetToken(String email) {
        Optional<User> userOpt = userRepo.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String token = UUID.randomUUID().toString();

            user.setResetToken(token);
            user.setResetTokenExpiry(Instant.now().plus(15, ChronoUnit.MINUTES));
            userRepo.save(user);

            // Publish the event to Kafka so the Notification Service can email the user
            userEventProducer.publishPasswordResetEvent(user.getEmail(), user.getName(), token);

            log.info("Password reset token generated and event published for: {}", email);
        } else {
            // Log for security auditing, but don't throw an error to prevent "Email Enumeration" attacks
            log.warn("Password reset requested for non-existent email: {}", email);
        }
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepo.findByResetToken(token)
                .orElseThrow(() -> new BadRequestException("Invalid or expired token"));

        if (user.getResetTokenExpiry().isBefore(Instant.now())) {
            throw new BadRequestException("Token has expired. Please request a new password reset.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);

        // Auto-unlock the account if they were locked out!
        user.setAccountNonLocked(true);
        user.setFailedAttemptCount(0);

        userRepo.save(user);
        log.info("Password reset successfully for user ID: {}", user.getId());
    }

    private String generateOtp() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }
}
