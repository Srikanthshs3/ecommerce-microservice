package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.entity.RefreshToken;
import org.praskanth.entity.User;
import org.praskanth.exception.UnauthorizedException;
import org.praskanth.repository.RefreshTokenRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Multi-device refresh tokens with rotation and reuse detection:
 *  - every login starts a new token family (one per device/session);
 *  - every refresh revokes the presented token and issues a child in the same family;
 *  - presenting an already-rotated token means it was stolen or replayed, so the whole family is revoked.
 * Only SHA-256 hashes are stored.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepo refreshTokenRepo;

    @Value("${jwt.refresh-expiration-days:7}")
    private int refreshExpirationDays;

    public record RotatedToken(User user, String newRawToken) {}

    public Duration getRefreshTokenDuration() {
        return Duration.ofDays(refreshExpirationDays);
    }

    /** Starts a new session (new family) and returns the raw token to hand to the client once. */
    @Transactional
    public String issueNewSession(User user) {
        return create(user, UUID.randomUUID().toString());
    }

    /**
     * noRollbackFor: when reuse is detected we revoke the family and then throw; that revocation must commit.
     */
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public RotatedToken rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new UnauthorizedException("Your session has ended. Please sign in again.");
        }
        RefreshToken current = refreshTokenRepo.findByTokenHashForUpdate(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Your session has ended. Please sign in again."));

        if (current.isRevoked()) {
            if (current.getFamilyId() != null) {
                int revoked = refreshTokenRepo.revokeFamily(current.getFamilyId());
                log.warn("Refresh token reuse detected for user {}; revoked {} active session token(s)",
                        current.getUser().getId(), revoked);
            }
            throw new UnauthorizedException("Your session has ended. Please sign in again.");
        }
        if (current.getExpiryDate().isBefore(Instant.now())) {
            throw new UnauthorizedException("Your session has expired. Please sign in again.");
        }

        current.setRevoked(true);
        User user = current.getUser();
        String familyId = current.getFamilyId() != null ? current.getFamilyId() : UUID.randomUUID().toString();
        return new RotatedToken(user, create(user, familyId));
    }

    /** Logout of the current device only. Unknown tokens are ignored (idempotent). */
    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        refreshTokenRepo.findByTokenHashForUpdate(hash(rawToken)).ifPresent(t -> {
            if (t.getFamilyId() != null) {
                refreshTokenRepo.revokeFamily(t.getFamilyId());
            } else {
                t.setRevoked(true);
            }
        });
    }

    /** Logout everywhere: password change/reset, role change, ban, account deletion. */
    @Transactional
    public void revokeAllForUser(Long userId) {
        refreshTokenRepo.revokeAllByUserId(userId);
    }

    @Transactional
    public int purgeExpired() {
        return refreshTokenRepo.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
    }

    private String create(User user, String familyId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenRepo.save(RefreshToken.builder()
                .tokenHash(hash(raw))
                .user(user)
                .familyId(familyId)
                .expiryDate(Instant.now().plus(Duration.ofDays(refreshExpirationDays)))
                .revoked(false)
                .build());
        return raw;
    }

    static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
