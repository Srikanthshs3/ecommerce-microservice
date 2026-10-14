package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import org.praskanth.exception.TooManyRequestsException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Temporary brute-force lockout per email, kept in Redis. Replaces the old permanent DB lock, which let
 * anyone lock any victim's account forever just by guessing wrong passwords. Atomic INCR, auto-expiring.
 * (Add per-IP limiting at the API gateway for distributed attacks.)
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "login:fail:";

    private final StringRedisTemplate redis;

    public void assertNotLocked(String email) {
        String key = KEY_PREFIX + email;
        String value = redis.opsForValue().get(key);
        if (value != null && Integer.parseInt(value) >= MAX_FAILURES) {
            Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
            long retryAfter = (ttl == null || ttl < 0) ? WINDOW.toSeconds() : ttl;
            throw new TooManyRequestsException(
                    "Too many failed login attempts. Try again later or reset your password.", retryAfter);
        }
    }

    public void recordFailure(String email) {
        String key = KEY_PREFIX + email;
        Long failures = redis.opsForValue().increment(key);
        if (failures != null && failures == 1L) {
            redis.expire(key, WINDOW);
        }
    }

    public void reset(String email) {
        redis.delete(KEY_PREFIX + email);
    }
}
