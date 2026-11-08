package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import org.praskanth.exception.TooManyRequestsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Counts consecutive failed logins per email (Redis, atomic INCR, auto-expiring).
 *
 *   failures 1-2   plain "invalid email or password"
 *   failures >= security.login.captcha-after (3)   the next attempt needs a CAPTCHA
 *   failures >= security.login.max-failures  (6)   the account is blocked in the database; only an admin unblocks it
 *
 * The counter is cleared by a successful login, a completed password reset, and by an admin unblocking the user.
 * It expires on its own after security.login.window-hours (24) without further failures.
 * (The block itself is the account_non_locked flag in the database and does not expire.)
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String KEY_PREFIX = "login:fail:";
    /** Administrators are never blocked for good (nobody could unblock them): they get a short cool-down instead. */
    private static final Duration ADMIN_COOLDOWN = Duration.ofMinutes(15);

    private final StringRedisTemplate redis;

    @Value("${security.login.captcha-after:3}")
    private int captchaAfter;

    @Value("${security.login.max-failures:6}")
    private int maxFailures;

    @Value("${security.login.window-hours:24}")
    private long windowHours;

    public int captchaAfter() {
        return captchaAfter;
    }

    public int maxFailures() {
        return maxFailures;
    }

    public int failures(String email) {
        String value = redis.opsForValue().get(KEY_PREFIX + email);
        return value == null ? 0 : Integer.parseInt(value);
    }

    /** Adds one failure and returns the new total. */
    public int recordFailure(String email) {
        String key = KEY_PREFIX + email;
        Long failures = redis.opsForValue().increment(key);
        if (failures != null && failures == 1L) {
            redis.expire(key, Duration.ofHours(windowHours));
        }
        return failures == null ? 1 : failures.intValue();
    }

    /** Administrators only: once the limit is reached, refuse further attempts for a short time. */
    public void startAdminCooldown(String email) {
        redis.expire(KEY_PREFIX + email, ADMIN_COOLDOWN);
    }

    public void assertAdminNotCoolingDown(String email) {
        String key = KEY_PREFIX + email;
        if (failures(email) >= maxFailures) {
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            long retryAfter = (ttl == null || ttl < 0) ? ADMIN_COOLDOWN.toSeconds() : ttl;
            throw new TooManyRequestsException(
                    "Too many failed login attempts. Try again in a few minutes.", retryAfter);
        }
    }

    public void reset(String email) {
        redis.delete(KEY_PREFIX + email);
    }
}