package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.OtpVerifyResponse;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.UnauthorizedException;
import org.praskanth.kafka.UserEventProducer;
import org.praskanth.repository.UserRepo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

/**
 * Email ownership proof shared by customer and seller registration:
 * OTP by email -> short-lived verification token -> consumed exactly once by the registration call.
 * All state lives in Redis with TTLs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailVerificationService {

    private static final Duration OTP_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final Duration VERIFIED_TOKEN_TTL = Duration.ofMinutes(15);
    private static final int MAX_OTP_ATTEMPTS = 5;

    private static final String OTP_KEY_PREFIX = "otp:register:";
    private static final String OTP_ATTEMPTS_PREFIX = "otp:register:attempts:";
    private static final String RESEND_LOCK_PREFIX = "otp:register:resend-lock:";
    private static final String VERIFIED_TOKEN_PREFIX = "otp:register:verified:";

    private final StringRedisTemplate redis;
    private final UserRepo userRepo;
    private final UserEventProducer eventProducer;

    private final SecureRandom secureRandom = new SecureRandom();

    /** @param email already normalised (trimmed, lower-case) */
    public void sendOtp(String email) {
        if (userRepo.existsByEmail(email)) {
            throw new ConflictException("An account with this email already exists");
        }
        Boolean acquired = redis.opsForValue().setIfAbsent(RESEND_LOCK_PREFIX + email, "1", RESEND_COOLDOWN);
        if (Boolean.FALSE.equals(acquired)) {
            throw new BadRequestException("Please wait before requesting another OTP");
        }
        String otp = String.valueOf(100000 + secureRandom.nextInt(900000));
        redis.opsForValue().set(OTP_KEY_PREFIX + email, otp, OTP_TTL);
        redis.delete(OTP_ATTEMPTS_PREFIX + email);
        eventProducer.publishSendOtpEmail(email, otp);
        log.info("Registration OTP issued for {}", email); // never log the OTP itself
    }

    public OtpVerifyResponse verifyOtp(String email, String otp) {
        String otpKey = OTP_KEY_PREFIX + email;
        String attemptsKey = OTP_ATTEMPTS_PREFIX + email;

        String stored = redis.opsForValue().get(otpKey);
        if (stored == null) {
            throw new BadRequestException("OTP expired or was never requested. Please request a new one");
        }

        // Count the attempt BEFORE comparing so parallel guesses cannot out-run the limit.
        Long attempts = redis.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redis.expire(attemptsKey, OTP_TTL);
        }
        if (attempts != null && attempts > MAX_OTP_ATTEMPTS) {
            redis.delete(otpKey);
            redis.delete(attemptsKey);
            throw new BadRequestException("Too many incorrect attempts. Please request a new OTP");
        }
        if (!constantTimeEquals(stored, otp)) {
            throw new BadRequestException("Invalid OTP");
        }

        redis.delete(otpKey);
        redis.delete(attemptsKey);

        byte[] raw = new byte[32];
        secureRandom.nextBytes(raw);
        String verificationToken = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        redis.opsForValue().set(VERIFIED_TOKEN_PREFIX + email, verificationToken, VERIFIED_TOKEN_TTL);

        log.info("Email verified for {}", email);
        return new OtpVerifyResponse(verificationToken, VERIFIED_TOKEN_TTL.toSeconds());
    }

    /** Checks the token without spending it (so cheap validation can fail without forcing a re-verify). */
    public void assertVerified(String email, String token) {
        String stored = redis.opsForValue().get(VERIFIED_TOKEN_PREFIX + email);
        if (stored == null || token == null || !constantTimeEquals(stored, token)) {
            throw new UnauthorizedException("Email verification expired or invalid. Please verify your email again");
        }
    }

    /**
     * Spends the token. DEL is atomic and reports whether this caller removed the key, so of two
     * concurrent registrations with the same token exactly one proceeds.
     */
    public void consume(String email, String token) {
        assertVerified(email, token);
        if (!Boolean.TRUE.equals(redis.delete(VERIFIED_TOKEN_PREFIX + email))) {
            throw new UnauthorizedException("Email verification was already used. Please verify your email again");
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
