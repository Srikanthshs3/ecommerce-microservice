package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/** Keeps refresh_tokens from growing forever now that rotated tokens are retained for reuse detection. */
@Configuration
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenCleanupJob {

    private final RefreshTokenService refreshTokenService;

    @Scheduled(cron = "${jwt.cleanup-cron:0 30 3 * * *}")
    public void purgeExpiredTokens() {
        int deleted = refreshTokenService.purgeExpired();
        log.info("Purged {} expired refresh tokens", deleted);
    }
}
