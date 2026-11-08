package org.praskanth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.dto.OtpVerifyResponse;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.UnauthorizedException;
import org.praskanth.kafka.UserEventProducer;
import org.praskanth.repository.UserRepo;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    private static final String EMAIL = "a@b.com";
    private static final String OTP_KEY = "otp:register:" + EMAIL;
    private static final String ATTEMPTS_KEY = "otp:register:attempts:" + EMAIL;
    private static final String VERIFIED_KEY = "otp:register:verified:" + EMAIL;

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @Mock UserRepo userRepo;
    @Mock UserEventProducer eventProducer;
    @InjectMocks EmailVerificationService service;

    // ---------------------------------------------------------------- sendOtp

    @Test
    @DisplayName("sendOtp for an already registered email is a conflict")
    void sendOtp_existingEmail_conflict() {
        when(userRepo.existsByEmail(EMAIL)).thenReturn(true);

        assertThatThrownBy(() -> service.sendOtp(EMAIL)).isInstanceOf(ConflictException.class);
        verify(eventProducer, never()).publishSendOtpEmail(any(), any());
    }

    @Test
    @DisplayName("sendOtp within the resend cool-down is rejected")
    void sendOtp_cooldown_badRequest() {
        when(userRepo.existsByEmail(EMAIL)).thenReturn(false);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent("otp:register:resend-lock:" + EMAIL, "1", Duration.ofSeconds(60))).thenReturn(false);

        assertThatThrownBy(() -> service.sendOtp(EMAIL))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("wait");
        verify(eventProducer, never()).publishSendOtpEmail(any(), any());
    }

    @Test
    @DisplayName("sendOtp stores a 6-digit OTP for 5 minutes, clears attempts and publishes the mail command")
    void sendOtp_ok_storesAndPublishes() {
        when(userRepo.existsByEmail(EMAIL)).thenReturn(false);
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent("otp:register:resend-lock:" + EMAIL, "1", Duration.ofSeconds(60))).thenReturn(true);

        service.sendOtp(EMAIL);

        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(ops).set(org.mockito.ArgumentMatchers.eq(OTP_KEY), stored.capture(), org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(5)));
        assertThat(stored.getValue()).matches("[1-9][0-9]{5}");
        verify(redis).delete(ATTEMPTS_KEY);
        verify(eventProducer).publishSendOtpEmail(EMAIL, stored.getValue());
    }

    // ---------------------------------------------------------------- verifyOtp

    @Test
    @DisplayName("verifyOtp without a stored OTP says it expired")
    void verifyOtp_noStoredOtp_badRequest() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(OTP_KEY)).thenReturn(null);

        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "123456"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expired");
        verify(ops, never()).increment(anyString());
    }

    @Test
    @DisplayName("verifyOtp with the wrong OTP counts the attempt and fails")
    void verifyOtp_wrongOtp_invalid() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(OTP_KEY)).thenReturn("111111");
        when(ops.increment(ATTEMPTS_KEY)).thenReturn(2L);

        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "222222"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("That code isn't correct. Please check it and try again.");
        verify(redis, never()).delete(OTP_KEY);
    }

    @Test
    @DisplayName("the first attempt starts the attempt-counter expiry")
    void verifyOtp_firstAttempt_setsCounterExpiry() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(OTP_KEY)).thenReturn("111111");
        when(ops.increment(ATTEMPTS_KEY)).thenReturn(1L);

        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "000000")).isInstanceOf(BadRequestException.class);

        verify(redis).expire(ATTEMPTS_KEY, Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("the fifth attempt is still allowed (boundary)")
    void verifyOtp_fifthAttempt_stillAllowed() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(OTP_KEY)).thenReturn("111111");
        when(ops.increment(ATTEMPTS_KEY)).thenReturn(5L);

        OtpVerifyResponse response = service.verifyOtp(EMAIL, "111111");

        assertThat(response.getVerificationToken()).isNotBlank();
    }

    @Test
    @DisplayName("the sixth attempt burns the OTP even if it is correct")
    void verifyOtp_sixthAttempt_locksOut() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(OTP_KEY)).thenReturn("111111");
        when(ops.increment(ATTEMPTS_KEY)).thenReturn(6L);

        assertThatThrownBy(() -> service.verifyOtp(EMAIL, "111111"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Too many");
        verify(redis).delete(OTP_KEY);
        verify(redis).delete(ATTEMPTS_KEY);
        verify(ops, never()).set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("verifyOtp with the right OTP consumes it and returns a 15 minute verification token")
    void verifyOtp_correct_returnsToken() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(OTP_KEY)).thenReturn("111111");
        when(ops.increment(ATTEMPTS_KEY)).thenReturn(1L);

        OtpVerifyResponse response = service.verifyOtp(EMAIL, "111111");

        assertThat(response.getExpiresInSeconds()).isEqualTo(900L);
        verify(redis).delete(OTP_KEY);
        verify(redis).delete(ATTEMPTS_KEY);
        verify(ops).set(VERIFIED_KEY, response.getVerificationToken(), Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("a null increment result does not block verification")
    void verifyOtp_nullAttempts_proceeds() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(OTP_KEY)).thenReturn("111111");
        when(ops.increment(ATTEMPTS_KEY)).thenReturn(null);

        assertThat(service.verifyOtp(EMAIL, "111111").getVerificationToken()).isNotBlank();
    }

    // ---------------------------------------------------------------- assertVerified / consume

    @Test
    @DisplayName("assertVerified passes for the matching token")
    void assertVerified_match_ok() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(VERIFIED_KEY)).thenReturn("tok");

        assertThatCode(() -> service.assertVerified(EMAIL, "tok")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertVerified fails when nothing is stored")
    void assertVerified_nothingStored_unauthorized() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(VERIFIED_KEY)).thenReturn(null);

        assertThatThrownBy(() -> service.assertVerified(EMAIL, "tok")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("assertVerified fails for a null token")
    void assertVerified_nullToken_unauthorized() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(VERIFIED_KEY)).thenReturn("tok");

        assertThatThrownBy(() -> service.assertVerified(EMAIL, null)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("assertVerified fails for a different token")
    void assertVerified_mismatch_unauthorized() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(VERIFIED_KEY)).thenReturn("tok");

        assertThatThrownBy(() -> service.assertVerified(EMAIL, "other")).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    @DisplayName("consume deletes the token when this caller removed it")
    void consume_ok_deletesKey() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(VERIFIED_KEY)).thenReturn("tok");
        when(redis.delete(VERIFIED_KEY)).thenReturn(true);

        assertThatCode(() -> service.consume(EMAIL, "tok")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("consume fails when a concurrent caller already removed the token")
    void consume_alreadyUsed_unauthorized() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(VERIFIED_KEY)).thenReturn("tok");
        when(redis.delete(VERIFIED_KEY)).thenReturn(false);

        assertThatThrownBy(() -> service.consume(EMAIL, "tok"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("already used");
    }

    @Test
    @DisplayName("consume with an invalid token fails before deleting anything")
    void consume_invalid_noDelete() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(VERIFIED_KEY)).thenReturn("tok");

        assertThatThrownBy(() -> service.consume(EMAIL, "bad")).isInstanceOf(UnauthorizedException.class);
        verify(redis, never()).delete(VERIFIED_KEY);
    }
}
