package org.praskanth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.exception.TooManyRequestsException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final String KEY = "login:fail:a@b.com";

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @InjectMocks LoginAttemptService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "captchaAfter", 3);
        ReflectionTestUtils.setField(service, "maxFailures", 6);
        ReflectionTestUtils.setField(service, "windowHours", 24L);
    }

    @Test
    @DisplayName("thresholds are exposed")
    void thresholds() {
        assertThat(service.captchaAfter()).isEqualTo(3);
        assertThat(service.maxFailures()).isEqualTo(6);
    }

    @Test
    @DisplayName("failures returns 0 when no counter exists")
    void failures_noKey_zero() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(KEY)).thenReturn(null);

        assertThat(service.failures("a@b.com")).isZero();
    }

    @Test
    @DisplayName("failures parses the stored counter")
    void failures_existing_parsed() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(KEY)).thenReturn("4");

        assertThat(service.failures("a@b.com")).isEqualTo(4);
    }

    @Test
    @DisplayName("first failure sets the expiry window")
    void recordFailure_first_setsExpiry() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(KEY)).thenReturn(1L);

        int count = service.recordFailure("a@b.com");

        assertThat(count).isEqualTo(1);
        verify(redis).expire(KEY, Duration.ofHours(24));
    }

    @Test
    @DisplayName("later failures do not extend the expiry window")
    void recordFailure_subsequent_noExpiry() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(KEY)).thenReturn(4L);

        int count = service.recordFailure("a@b.com");

        assertThat(count).isEqualTo(4);
        verify(redis, never()).expire(any(String.class), any(Duration.class));
    }

    @Test
    @DisplayName("a null increment result counts as one failure")
    void recordFailure_nullIncrement_one() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(KEY)).thenReturn(null);

        assertThat(service.recordFailure("a@b.com")).isEqualTo(1);
    }

    @Test
    @DisplayName("startAdminCooldown shortens the key TTL to 15 minutes")
    void startAdminCooldown_setsFifteenMinutes() {
        service.startAdminCooldown("a@b.com");

        verify(redis).expire(KEY, Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("assertAdminNotCoolingDown passes below the limit")
    void assertAdminNotCoolingDown_belowLimit_ok() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(KEY)).thenReturn("5");

        assertThatCode(() -> service.assertAdminNotCoolingDown("a@b.com")).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("assertAdminNotCoolingDown at the limit reports the remaining TTL")
    void assertAdminNotCoolingDown_atLimit_throwsWithTtl() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(KEY)).thenReturn("6");
        when(redis.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(120L);

        assertThatThrownBy(() -> service.assertAdminNotCoolingDown("a@b.com"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(120L));
    }

    @Test
    @DisplayName("assertAdminNotCoolingDown falls back to 900s when the key has no TTL")
    void assertAdminNotCoolingDown_noTtl_defaultsTo900() {
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.get(KEY)).thenReturn("7");
        when(redis.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(-1L);

        assertThatThrownBy(() -> service.assertAdminNotCoolingDown("a@b.com"))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(900L));
    }

    @Test
    @DisplayName("reset deletes the counter")
    void reset_deletesKey() {
        service.reset("a@b.com");

        verify(redis).delete(KEY);
    }
}
