package org.praskanth.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.dto.LoginRequest;
import org.praskanth.dto.OtpVerifyResponse;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.praskanth.exception.AuthChallengeException;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.exception.TooManyRequestsException;
import org.praskanth.exception.UnauthorizedException;
import org.praskanth.kafka.UserEventProducer;
import org.praskanth.security.JwtUtils;
import org.praskanth.service.AuthService;
import org.praskanth.service.CaptchaService;
import org.praskanth.service.EmailVerificationService;
import org.praskanth.service.LoginAttemptService;
import org.praskanth.service.RefreshTokenService;
import org.praskanth.repository.UserRepo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String EMAIL = "alice@example.com";

    @Mock UserRepo userRepo;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtUtils jwtUtils;
    @Mock RefreshTokenService refreshTokenService;
    @Mock LoginAttemptService loginAttemptService;
    @Mock CaptchaService captchaService;
    @Mock EmailVerificationService emailVerificationService;
    @Mock UserEventProducer eventProducer;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "dummyPasswordHash", "dummy-hash");
    }

    private static LoginRequest request(String email, String password, String captcha) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        r.setCaptchaToken(captcha);
        return r;
    }

    private void counters(int failures) {
        when(loginAttemptService.failures(EMAIL)).thenReturn(failures);
        when(loginAttemptService.maxFailures()).thenReturn(6);
        when(loginAttemptService.captchaAfter()).thenReturn(3);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    // ---------------------------------------------------------------- registration

    @Test
    @DisplayName("initiateRegistration normalises the email before sending the OTP")
    void initiateRegistration_mixedCaseEmail_sendsOtpToNormalisedEmail() {
        service.initiateRegistration("  Alice@Example.COM ");

        verify(emailVerificationService).sendOtp(EMAIL);
    }

    @Test
    @DisplayName("verifyRegistrationOtp delegates with a normalised email")
    void verifyRegistrationOtp_delegates() {
        OtpVerifyResponse response = new OtpVerifyResponse("tok", 600);
        when(emailVerificationService.verifyOtp(EMAIL, "123456")).thenReturn(response);

        OtpVerifyResponse result = service.verifyRegistrationOtp("ALICE@example.com", "123456");

        assertThat(result).isSameAs(response);
    }

    // ---------------------------------------------------------------- login

    @Test
    @DisplayName("login succeeds: counters reset, session issued, token body built")
    void login_validCredentials_returnsTokenAndRefresh() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        counters(0);
        when(captchaService.isEnabled()).thenReturn(false);
        when(passwordEncoder.matches("secret", "encoded-pw")).thenReturn(true);
        when(refreshTokenService.issueNewSession(user)).thenReturn("raw-refresh");
        when(jwtUtils.generateAccessToken(user)).thenReturn("jwt");
        when(jwtUtils.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthService.LoginResult result = service.login(request("Alice@Example.com", "secret", null));

        assertThat(result.refreshToken()).isEqualTo("raw-refresh");
        assertThat(result.body().getToken()).isEqualTo("jwt");
        assertThat(result.body().getExpiresIn()).isEqualTo(900L);
        assertThat(result.body().getEmail()).isEqualTo(EMAIL);
        assertThat(result.body().getRole()).isEqualTo("CUSTOMER");
        assertThat(result.body().getRoles()).containsExactly("CUSTOMER");
        verify(loginAttemptService).reset(EMAIL);
    }

    @Test
    @DisplayName("login returns the highest-privilege role as primary role")
    void login_multipleRoles_primaryRoleIsHighest() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER, UserRole.SELLER);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        counters(0);
        when(captchaService.isEnabled()).thenReturn(false);
        when(passwordEncoder.matches("secret", "encoded-pw")).thenReturn(true);
        when(refreshTokenService.issueNewSession(user)).thenReturn("r");
        when(jwtUtils.generateAccessToken(user)).thenReturn("jwt");
        when(jwtUtils.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthService.LoginResult result = service.login(request(EMAIL, "secret", null));

        assertThat(result.body().getRole()).isEqualTo("SELLER");
        assertThat(result.body().getRoles()).containsExactlyInAnyOrder("CUSTOMER", "SELLER");
    }

    @Test
    @DisplayName("login on a locked account is rejected with ACCOUNT_LOCKED before the password is checked")
    void login_lockedAccount_throwsAccountLocked() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        user.setAccountNonLocked(false);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        counters(0);

        assertThatThrownBy(() -> service.login(request(EMAIL, "secret", null)))
                .isInstanceOfSatisfying(AuthChallengeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("ACCOUNT_LOCKED");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                });
        verify(passwordEncoder, never()).matches(any(), any());
        verify(loginAttemptService, never()).recordFailure(anyString());
    }

    @Test
    @DisplayName("unknown email behaves like a blocked account once the failure limit is reached")
    void login_unknownEmailAtLimit_throwsAccountLocked() {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.empty());
        counters(6);

        assertThatThrownBy(() -> service.login(request(EMAIL, "secret", null)))
                .isInstanceOfSatisfying(AuthChallengeException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_LOCKED"));
    }

    @Test
    @DisplayName("unknown email gets the generic error and is compared against the dummy hash")
    void login_unknownEmail_genericUnauthorized() {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.empty());
        counters(0);
        when(captchaService.isEnabled()).thenReturn(false);
        when(passwordEncoder.matches("secret", "dummy-hash")).thenReturn(false);
        when(loginAttemptService.recordFailure(EMAIL)).thenReturn(1);

        assertThatThrownBy(() -> service.login(request(EMAIL, "secret", null)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("wrong password below the captcha threshold: plain error")
    void login_wrongPassword_plainError() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        counters(1);
        when(captchaService.isEnabled()).thenReturn(true);
        when(passwordEncoder.matches("bad", "encoded-pw")).thenReturn(false);
        when(loginAttemptService.recordFailure(EMAIL)).thenReturn(2);

        assertThatThrownBy(() -> service.login(request(EMAIL, "bad", null)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
        verify(captchaService, never()).verify(any());
    }

    @Test
    @DisplayName("third wrong password with captcha enabled: 401 CAPTCHA_REQUIRED and attempts-left message")
    void login_thirdFailureCaptchaOn_challenge() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        counters(2);
        when(captchaService.isEnabled()).thenReturn(true);
        when(passwordEncoder.matches("bad", "encoded-pw")).thenReturn(false);
        when(loginAttemptService.recordFailure(EMAIL)).thenReturn(3);

        assertThatThrownBy(() -> service.login(request(EMAIL, "bad", null)))
                .isInstanceOfSatisfying(AuthChallengeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("CAPTCHA_REQUIRED");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getMessage()).contains("3 attempts left");
                });
    }

    @Test
    @DisplayName("fifth wrong password: singular 'attempt' wording")
    void login_fifthFailure_singularWording() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        counters(4);
        when(captchaService.isEnabled()).thenReturn(true);
        when(captchaService.verify("tok")).thenReturn(true);
        when(passwordEncoder.matches("bad", "encoded-pw")).thenReturn(false);
        when(loginAttemptService.recordFailure(EMAIL)).thenReturn(5);

        assertThatThrownBy(() -> service.login(request(EMAIL, "bad", "tok")))
                .isInstanceOfSatisfying(AuthChallengeException.class,
                        e -> assertThat(e.getMessage()).contains("1 attempt left").doesNotContain("attempts"));
    }

    @Test
    @DisplayName("third wrong password with captcha disabled: plain 401 with attempts-left message")
    void login_thirdFailureCaptchaOff_unauthorizedWithCounter() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        counters(2);
        when(captchaService.isEnabled()).thenReturn(false);
        when(passwordEncoder.matches("bad", "encoded-pw")).thenReturn(false);
        when(loginAttemptService.recordFailure(EMAIL)).thenReturn(3);

        assertThatThrownBy(() -> service.login(request(EMAIL, "bad", null)))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("3 attempts left");
    }

    @Test
    @DisplayName("after 3 failures a missing captcha token is rejected without counting a failure")
    void login_captchaMissing_challengeAndNotCounted() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        counters(3);
        when(captchaService.isEnabled()).thenReturn(true);
        when(captchaService.verify(null)).thenReturn(false);

        assertThatThrownBy(() -> service.login(request(EMAIL, "secret", null)))
                .isInstanceOfSatisfying(AuthChallengeException.class, e -> {
                    assertThat(e.getCode()).isEqualTo("CAPTCHA_REQUIRED");
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getMessage()).contains("not a robot");
                });
        verify(loginAttemptService, never()).recordFailure(anyString());
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    @DisplayName("after 3 failures an invalid captcha token gets the 'check failed' message")
    void login_captchaInvalid_challengeFailedMessage() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        counters(3);
        when(captchaService.isEnabled()).thenReturn(true);
        when(captchaService.verify("bad-token")).thenReturn(false);

        assertThatThrownBy(() -> service.login(request(EMAIL, "secret", "bad-token")))
                .isInstanceOfSatisfying(AuthChallengeException.class,
                        e -> assertThat(e.getMessage()).contains("CAPTCHA check failed"));
    }

    @Test
    @DisplayName("after 3 failures a valid captcha lets the correct password through")
    void login_captchaValid_proceeds() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        counters(3);
        when(captchaService.isEnabled()).thenReturn(true);
        when(captchaService.verify("good")).thenReturn(true);
        when(passwordEncoder.matches("secret", "encoded-pw")).thenReturn(true);
        when(refreshTokenService.issueNewSession(user)).thenReturn("r");
        when(jwtUtils.generateAccessToken(user)).thenReturn("jwt");
        when(jwtUtils.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthService.LoginResult result = service.login(request(EMAIL, "secret", "good"));

        assertThat(result.body().getToken()).isEqualTo("jwt");
        verify(loginAttemptService).reset(EMAIL);
    }

    @Test
    @DisplayName("sixth wrong password locks the account, persists it and revokes all sessions")
    void login_sixthFailure_locksAccount() {
        User user = TestData.user(7L, EMAIL, UserRole.CUSTOMER);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        counters(5);
        when(captchaService.isEnabled()).thenReturn(false);
        when(passwordEncoder.matches("bad", "encoded-pw")).thenReturn(false);
        when(loginAttemptService.recordFailure(EMAIL)).thenReturn(6);

        assertThatThrownBy(() -> service.login(request(EMAIL, "bad", null)))
                .isInstanceOfSatisfying(AuthChallengeException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_LOCKED"));

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(saved.capture());
        assertThat(saved.getValue().isAccountNonLocked()).isFalse();
        verify(refreshTokenService).revokeAllForUser(7L);
    }

    @Test
    @DisplayName("unknown email reaching the limit is reported as locked but nothing is saved")
    void login_unknownEmailReachesLimit_lockedWithoutSave() {
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.empty());
        counters(5);
        when(captchaService.isEnabled()).thenReturn(false);
        when(passwordEncoder.matches("bad", "dummy-hash")).thenReturn(false);
        when(loginAttemptService.recordFailure(EMAIL)).thenReturn(6);

        assertThatThrownBy(() -> service.login(request(EMAIL, "bad", null)))
                .isInstanceOf(AuthChallengeException.class);
        verify(userRepo, never()).save(any());
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("admin reaching the limit gets a 15 minute cool-down instead of a permanent lock")
    void login_adminReachesLimit_cooldown() {
        User admin = TestData.user(2L, EMAIL, UserRole.ADMIN);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(admin));
        counters(5);
        when(captchaService.isEnabled()).thenReturn(false);
        when(passwordEncoder.matches("bad", "encoded-pw")).thenReturn(false);
        when(loginAttemptService.recordFailure(EMAIL)).thenReturn(6);

        assertThatThrownBy(() -> service.login(request(EMAIL, "bad", null)))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(900L));
        verify(loginAttemptService).startAdminCooldown(EMAIL);
        verify(userRepo, never()).save(any());
        assertThat(admin.isAccountNonLocked()).isTrue();
    }

    @Test
    @DisplayName("admin during the cool-down is rejected before the password is checked")
    void login_adminCoolingDown_rejected() {
        User admin = TestData.user(2L, EMAIL, UserRole.ADMIN);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(admin));
        counters(6);
        doThrow(new TooManyRequestsException("slow down", 120L)).when(loginAttemptService).assertAdminNotCoolingDown(EMAIL);

        assertThatThrownBy(() -> service.login(request(EMAIL, "secret", null)))
                .isInstanceOf(TooManyRequestsException.class);
        verify(passwordEncoder, never()).matches(any(), any());
    }

    // ---------------------------------------------------------------- refresh / logout

    @Test
    @DisplayName("refresh returns a new access token and the rotated refresh token")
    void refresh_validToken_returnsNewTokens() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        when(refreshTokenService.rotate("old")).thenReturn(new RefreshTokenService.RotatedToken(user, "new-raw"));
        when(jwtUtils.generateAccessToken(user)).thenReturn("jwt2");
        when(jwtUtils.getAccessTokenExpirationSeconds()).thenReturn(900L);

        AuthService.RefreshResult result = service.refresh("old");

        assertThat(result.refreshToken()).isEqualTo("new-raw");
        assertThat(result.body().getToken()).isEqualTo("jwt2");
        assertThat(result.body().getExpiresIn()).isEqualTo(900L);
    }

    @Test
    @DisplayName("refresh for a locked user revokes every session and answers 401")
    void refresh_lockedUser_revokesAllAndThrows() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        user.setAccountNonLocked(false);
        when(refreshTokenService.rotate("old")).thenReturn(new RefreshTokenService.RotatedToken(user, "new-raw"));

        assertThatThrownBy(() -> service.refresh("old"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("locked");
        verify(refreshTokenService).revokeAllForUser(1L);
        verify(jwtUtils, never()).generateAccessToken(any());
    }

    @Test
    @DisplayName("refresh propagates reuse detection errors from the token service")
    void refresh_reuseDetected_propagates() {
        when(refreshTokenService.rotate("stolen")).thenThrow(new UnauthorizedException("Refresh token was revoked. Please login again"));

        assertThatThrownBy(() -> service.refresh("stolen")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("logout revokes the presented token")
    void logout_revokesToken() {
        service.logout("raw");

        verify(refreshTokenService).revoke("raw");
    }

    @Test
    @DisplayName("logoutAllSessions revokes all tokens of the user")
    void logoutAllSessions_revokesAll() {
        service.logoutAllSessions(5L);

        verify(refreshTokenService).revokeAllForUser(5L);
    }

    // ---------------------------------------------------------------- password reset request

    @Test
    @DisplayName("requestPasswordReset inside the cool-down silently does nothing")
    void requestPasswordReset_cooldownActive_noop() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("pwd-reset:cooldown:" + EMAIL, "1", Duration.ofSeconds(60))).thenReturn(false);

        service.requestPasswordReset("Alice@Example.com");

        verify(userRepo, never()).findByEmail(anyString());
        verify(eventProducer, never()).publishPasswordResetEvent(any(), any(), any());
    }

    @Test
    @DisplayName("requestPasswordReset for an unknown email silently does nothing")
    void requestPasswordReset_unknownEmail_noop() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("pwd-reset:cooldown:" + EMAIL, "1", Duration.ofSeconds(60))).thenReturn(true);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.empty());

        service.requestPasswordReset(EMAIL);

        verify(eventProducer, never()).publishPasswordResetEvent(any(), any(), any());
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("requestPasswordReset for a blocked account issues no token")
    void requestPasswordReset_lockedUser_noToken() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        user.setAccountNonLocked(false);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("pwd-reset:cooldown:" + EMAIL, "1", Duration.ofSeconds(60))).thenReturn(true);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.requestPasswordReset(EMAIL);

        verify(eventProducer, never()).publishPasswordResetEvent(any(), any(), any());
        verify(valueOps, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("requestPasswordReset stores only the token hash (15 min TTL) and mails the raw token")
    void requestPasswordReset_knownUser_storesHashAndPublishes() throws Exception {
        User user = TestData.user(9L, EMAIL, UserRole.CUSTOMER);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent("pwd-reset:cooldown:" + EMAIL, "1", Duration.ofSeconds(60))).thenReturn(true);
        when(userRepo.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.requestPasswordReset(EMAIL);

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(eventProducer).publishPasswordResetEvent(eq(EMAIL), eq("Test User"), token.capture());
        assertThat(token.getValue()).isNotBlank();
        verify(valueOps).set("pwd-reset:token:" + sha256(token.getValue()), "9", Duration.ofMinutes(15));
    }

    // ---------------------------------------------------------------- password reset completion

    @Test
    @DisplayName("resetPassword with an unknown or used token is a bad request")
    void resetPassword_unknownToken_badRequest() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("pwd-reset:token:" + sha256("tok"))).thenReturn(null);

        assertThatThrownBy(() -> service.resetPassword("tok", "NewPass#1"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("This link has expired or is not valid. Please request a new one.");
    }

    @Test
    @DisplayName("resetPassword whose user no longer exists is a bad request")
    void resetPassword_userGone_badRequest() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("pwd-reset:token:" + sha256("tok"))).thenReturn("4");
        when(userRepo.findById(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("tok", "NewPass#1"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("resetPassword for a blocked account is refused and does not unblock it")
    void resetPassword_lockedUser_refused() throws Exception {
        User user = TestData.user(4L, EMAIL, UserRole.CUSTOMER);
        user.setAccountNonLocked(false);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("pwd-reset:token:" + sha256("tok"))).thenReturn("4");
        when(userRepo.findById(4L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword("tok", "NewPass#1"))
                .isInstanceOfSatisfying(AuthChallengeException.class,
                        e -> assertThat(e.getCode()).isEqualTo("ACCOUNT_LOCKED"));
        assertThat(user.isAccountNonLocked()).isFalse();
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("resetPassword sets the new hash, clears the throttle and revokes all sessions")
    void resetPassword_valid_updatesPassword() throws Exception {
        User user = TestData.user(4L, EMAIL, UserRole.CUSTOMER);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.getAndDelete("pwd-reset:token:" + sha256("tok"))).thenReturn("4");
        when(userRepo.findById(4L)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass#1")).thenReturn("new-hash");

        service.resetPassword("tok", "NewPass#1");

        assertThat(user.getPassword()).isEqualTo("new-hash");
        verify(loginAttemptService).reset(EMAIL);
        verify(refreshTokenService).revokeAllForUser(4L);
    }

    // ---------------------------------------------------------------- change password

    @Test
    @DisplayName("changePassword for an unknown user is not found")
    void changePassword_unknownUser_notFound() {
        when(userRepo.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changePassword(1L, "old", "new"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("changePassword with a wrong current password is rejected")
    void changePassword_wrongCurrent_badRequest() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "encoded-pw")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(1L, "wrong", "new"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Current password is incorrect");
        verify(refreshTokenService, never()).revokeAllForUser(any());
    }

    @Test
    @DisplayName("changePassword to the same password is rejected")
    void changePassword_samePassword_badRequest() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("same", "encoded-pw")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(1L, "same", "same"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("different");
    }

    @Test
    @DisplayName("changePassword encodes the new password and revokes other sessions")
    void changePassword_valid_updatesAndRevokes() {
        User user = TestData.user(1L, EMAIL, UserRole.CUSTOMER);
        when(userRepo.findById(1L)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "encoded-pw")).thenReturn(true);
        when(passwordEncoder.matches("brand-new", "encoded-pw")).thenReturn(false);
        when(passwordEncoder.encode("brand-new")).thenReturn("hash2");

        service.changePassword(1L, "old", "brand-new");

        assertThat(user.getPassword()).isEqualTo("hash2");
        verify(refreshTokenService).revokeAllForUser(1L);
    }
}
