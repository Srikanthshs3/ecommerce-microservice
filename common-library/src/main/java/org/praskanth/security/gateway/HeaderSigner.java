package org.praskanth.security.gateway;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * HMAC-SHA256 signature over the identity headers the gateway forwards.
 *
 * Why: header-based trust is only as strong as the network. With a signature, a pod/container that
 * can reach a service directly still cannot forge "X-User-Roles: ADMIN" without the secret.
 *
 * NOTE: api-gateway has a byte-for-byte copy of this algorithm (it must not depend on this servlet-based
 * module). Keep both in sync - the canonical string and encoding below are the contract.
 */
public final class HeaderSigner {

    private static final String HMAC = "HmacSHA256";
    private final byte[] key;

    public HeaderSigner(String secret) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException("Gateway identity signing secret must be at least 32 characters");
        }
        this.key = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String sign(String userId, String email, String roles, long epochSeconds) {
        requireNoDelimiter(userId, email, roles);
        return hmac(canonical(userId, email, roles, Long.toString(epochSeconds)));
    }

    public boolean verify(String userId, String email, String roles, String timestamp,
                          String signature, long nowEpochSeconds, long maxSkewSeconds) {
        if (signature == null || timestamp == null || hasDelimiter(userId, email, roles)) {
            return false;
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return false;
        }
        if (Math.abs(nowEpochSeconds - ts) > maxSkewSeconds) {
            return false;
        }
        String expected = hmac(canonical(userId, email, roles, timestamp));
        // constant-time comparison
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private static String canonical(String userId, String email, String roles, String timestamp) {
        return n(userId) + "\n" + n(email) + "\n" + n(roles) + "\n" + timestamp;
    }

    private static String n(String s) {
        return s == null ? "" : s;
    }

    /**
     * Fields are joined with '\n'. If a field could contain '\n', two different (email, roles) pairs could
     * serialise to the same canonical string and share a signature. Refuse such input outright.
     */
    private static boolean hasDelimiter(String... fields) {
        for (String f : fields) {
            if (f != null && (f.indexOf('\n') >= 0 || f.indexOf('\r') >= 0)) {
                return true;
            }
        }
        return false;
    }

    private static void requireNoDelimiter(String... fields) {
        if (hasDelimiter(fields)) {
            throw new IllegalArgumentException("Identity header values must not contain line breaks");
        }
    }

    private String hmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC);
            mac.init(new SecretKeySpec(key, HMAC));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
