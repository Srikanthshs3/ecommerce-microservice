package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.OtpVerifyResponse;
import org.praskanth.dto.RegisterCompleteRequest;
import org.praskanth.dto.UserResponse;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.UnauthorizedException;
import org.praskanth.kafka.UserEventProducer;
import org.praskanth.repository.UserRepo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.UUID;

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

        User user = User.builder()
                .email(request.getEmail())
                .name(request.getName())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.CUSTOMER)
                .emailVerified(true)
                .createdAt(java.time.Instant.now())
                .build();

        User saved = userRepo.save(user);
        redisTemplate.delete(verifiedKey);

        userEventProducer.publishUserRegistered(saved);
        log.info("User registered: {}", saved.getEmail());

        return UserResponse.builder()
                .id(saved.getId())
                .email(saved.getEmail())
                .name(saved.getName())
                .role(saved.getRole().name())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    private String generateOtp() {
        int otp = 100000 + secureRandom.nextInt(900000);
        return String.valueOf(otp);
    }
}
