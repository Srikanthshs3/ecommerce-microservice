package org.praskanth.service.impl;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.LoginRequest;
import org.praskanth.dto.LoginResponse;
import org.praskanth.dto.OtpVerifyResponse;
import org.praskanth.dto.RefreshTokenResponse;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.exception.*;
import org.praskanth.kafka.UserEventProducer;
import org.praskanth.mapper.UserMapper;
import org.praskanth.repository.UserRepo;
import org.praskanth.security.JwtUtils;
import org.praskanth.service.AuthService;
import org.praskanth.service.CaptchaService;
import org.praskanth.service.EmailVerificationService;
import org.praskanth.service.LoginAttemptService;
import org.praskanth.service.RefreshTokenService;
import org.praskanth.util.Emails;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final Duration RESET_TOKEN_TTL = Duration.ofMinutes(15);
    private static final Duration RESET_COOLDOWN = Duration.ofSeconds(60);
    private static final String RESET_TOKEN_PREFIX = "pwd-reset:token:";
    private static final String RESET_COOLDOWN_PREFIX = "pwd-reset:cooldown:";

    private final UserRepo userRepo;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RefreshTokenService refreshTokenService;
    private final LoginAttemptService loginAttemptService;
    private final CaptchaService captchaService;
    private final EmailVerificationService emailVerificationService;
    private final UserEventProducer eventProducer;
    private final StringRedisTemplate redis;

    private final SecureRandom secureRandom = new SecureRandom();
    /** Compared against when the email is unknown so response time does not reveal which emails exist. */
    private String dummyPasswordHash;

    @PostConstruct
    void init() {
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalisation-only");
    }

    // ---------- registration (email verification step) ----------

    @Override
    public void initiateRegistration(String email) {
        emailVerificationService.sendOtp(Emails.normalize(email));
    }

    @Override
    public OtpVerifyResponse verifyRegistrationOtp(String email, String otp) {
        return emailVerificationService.verifyOtp(Emails.normalize(email), otp);
    }

    // ---------- login / refresh / logout ----------

    /**
     * Failed-login rules (numbers are configurable, see LoginAttemptService):
     *   1-2 wrong passwords   plain error
     *   3 wrong passwords     the next attempt must carry a valid CAPTCHA token (not counted if it is missing)
     *   6 wrong passwords     the account is blocked (account_non_locked = false). A blocked account cannot log in,
     *                         cannot request or complete a password reset, and only an admin can unblock it.
     * Administrators are never blocked for good; they get a 15-minute cool-down so they cannot lock themselves out.
     * noRollbackFor: the "blocked" flag must be saved even though we answer with an error.
     */
    @Override
    @Transactional(noRollbackFor = {UnauthorizedException.class, AuthChallengeException.class, TooManyRequestsException.class})
    public LoginResult login(LoginRequest request) {
        String email = Emails.normalize(request.getEmail());
        User user = userRepo.findByEmail(email).orElse(null);
        boolean admin = user != null && user.hasRole(UserRole.ADMIN);
        int failures = loginAttemptService.failures(email);
        int maxFailures = loginAttemptService.maxFailures();
        int captchaAfter = loginAttemptService.captchaAfter();

        // 1. already blocked? (unknown emails behave like blocked ones after the limit, so nothing is revealed)
        boolean blocked = user != null ? !user.isAccountNonLocked() : failures >= maxFailures;
        if (blocked) {
            throw accountBlocked();
        }
        if (admin) {
            loginAttemptService.assertAdminNotCoolingDown(email);
        }

        // 2. too many wrong passwords so far: the user must prove they are human first
        boolean captchaOn = captchaService.isEnabled();
        if (captchaOn && failures >= captchaAfter && !captchaService.verify(request.getCaptchaToken())) {
            String token = request.getCaptchaToken();
            throw new AuthChallengeException(HttpStatus.BAD_REQUEST, "CAPTCHA_REQUIRED",
                    token == null || token.isBlank()
                            ? "Please confirm that you are not a robot."
                            : "The CAPTCHA check failed. Please try again.");
        }

        // 3. the password itself
        boolean passwordOk = passwordEncoder.matches(request.getPassword(),
                user != null ? user.getPassword() : dummyPasswordHash);

        if (user == null || !passwordOk) {
            int count = loginAttemptService.recordFailure(email);
            if (count >= maxFailures) {
                if (admin) {
                    loginAttemptService.startAdminCooldown(email);
                    throw new TooManyRequestsException("Too many failed login attempts. Try again in a few minutes.", 900);
                }
                if (user != null) {
                    user.setAccountNonLocked(false);
                    userRepo.save(user);
                    refreshTokenService.revokeAllForUser(user.getId());
                    log.warn("User {} blocked after {} failed login attempts", user.getId(), count);
                }
                throw accountBlocked();
            }
            if (count >= captchaAfter) {
                int left = maxFailures - count;
                String message = "Invalid email or password. " + left + (left == 1 ? " attempt" : " attempts")
                        + " left before your account is blocked.";
                if (captchaOn) {
                    throw new AuthChallengeException(HttpStatus.UNAUTHORIZED, "CAPTCHA_REQUIRED",
                            message + " Please confirm that you are not a robot to try again.");
                }
                throw new UnauthorizedException(message);
            }
            throw new UnauthorizedException("Invalid email or password");
        }

        loginAttemptService.reset(email);
        String refreshToken = refreshTokenService.issueNewSession(user);
        Set<String> roles = UserMapper.roleNames(user);

        LoginResponse body = LoginResponse.builder()
                .token(jwtUtils.generateAccessToken(user))
                .expiresIn(jwtUtils.getAccessTokenExpirationSeconds())
                .email(user.getEmail())
                .name(user.getName())
                .role(UserMapper.primaryRole(roles))
                .roles(roles)
                .build();
        log.info("User {} logged in", user.getId());
        return new LoginResult(body, refreshToken);
    }

    private static AuthChallengeException accountBlocked() {
        return new AuthChallengeException(HttpStatus.FORBIDDEN, "ACCOUNT_LOCKED",
                "Your account is blocked. Please contact an administrator to unblock it.");
    }

    @Override
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RefreshResult refresh(String refreshToken) {
        RefreshTokenService.RotatedToken rotated = refreshTokenService.rotate(refreshToken);
        User user = rotated.user();

        if (!user.isAccountNonLocked()) {
            refreshTokenService.revokeAllForUser(user.getId());
            throw new UnauthorizedException("Account is locked. Please contact support.");
        }

        // Roles are re-read from the database here, so role/KYC-driven changes reach the token within one refresh.
        RefreshTokenResponse body = RefreshTokenResponse.builder()
                .token(jwtUtils.generateAccessToken(user))
                .expiresIn(jwtUtils.getAccessTokenExpirationSeconds())
                .build();
        return new RefreshResult(body, rotated.newRawToken());
    }

    @Override
    public void logout(String refreshToken) {
        refreshTokenService.revoke(refreshToken);
    }

    @Override
    public void logoutAllSessions(Long userId) {
        refreshTokenService.revokeAllForUser(userId);
    }

    // ---------- password management ----------

    @Override
    public void requestPasswordReset(String rawEmail) {
        String email = Emails.normalize(rawEmail);

        // Same silent outcome whether or not the account exists (no enumeration), and no mail-bombing.
        Boolean acquired = redis.opsForValue().setIfAbsent(RESET_COOLDOWN_PREFIX + email, "1", RESET_COOLDOWN);
        if (Boolean.FALSE.equals(acquired)) {
            return;
        }
        userRepo.findByEmail(email).ifPresentOrElse(user -> {
            if (!user.isAccountNonLocked()) {
                // Blocked accounts (too many failed logins, or blocked by an admin) cannot reset their password.
                log.warn("Password reset ignored for blocked user {}", user.getId());
                return;
            }
            byte[] bytes = new byte[32];
            secureRandom.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            // Only the hash is stored; the raw token exists only in the email.
            redis.opsForValue().set(RESET_TOKEN_PREFIX + sha256(token), String.valueOf(user.getId()), RESET_TOKEN_TTL);
            eventProducer.publishPasswordResetEvent(user.getEmail(), user.getName(), token);
            log.info("Password reset requested for user {}", user.getId());
        }, () -> log.warn("Password reset requested for unknown email"));
    }

    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        // GETDEL makes the token strictly single-use even under concurrent requests (needs Redis >= 6.2).
        String userId = redis.opsForValue().getAndDelete(RESET_TOKEN_PREFIX + sha256(token));
        if (userId == null) {
            throw new BadRequestException("This link has expired or is not valid. Please request a new one.");
        }
        User user = userRepo.findById(Long.valueOf(userId))
                .orElseThrow(() -> new BadRequestException("This link has expired or is not valid. Please request a new one."));
        if (!user.isAccountNonLocked()) {
            throw accountBlocked();
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        // Deliberately does NOT touch accountNonLocked: a reset must not undo an admin ban.
        loginAttemptService.reset(user.getEmail());
        refreshTokenService.revokeAllForUser(user.getId());
        log.info("Password reset completed for user {}", user.getId());
    }

    @Override
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepo.findById(userId).orElseThrow(() -> new NotFoundException("We couldn't find that user."));
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BadRequestException("New password must be different from the current password");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        refreshTokenService.revokeAllForUser(userId); // other devices must sign in again
        log.info("Password changed for user {}", userId);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
