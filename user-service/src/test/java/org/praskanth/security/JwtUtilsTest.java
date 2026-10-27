package org.praskanth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.praskanth.TestData;
import org.praskanth.entity.User;
import org.praskanth.enums.UserRole;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilsTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef-secret";

    private JwtUtils utils(String secret, String legacy) {
        JwtUtils u = new JwtUtils();
        ReflectionTestUtils.setField(u, "secret", secret);
        ReflectionTestUtils.setField(u, "legacySecret", legacy);
        ReflectionTestUtils.setField(u, "accessExpirationMinutes", 15L);
        ReflectionTestUtils.setField(u, "issuer", "test-issuer");
        return u;
    }

    private Claims parse(String token, String secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    @Test
    @DisplayName("generated token carries the gateway contract claims and is signed with the secret")
    void generateAccessToken_containsContractClaims() {
        JwtUtils u = utils(SECRET, "");
        ReflectionTestUtils.invokeMethod(u, "init");
        User user = TestData.user(42L, "alice@example.com", UserRole.CUSTOMER);

        String token = u.generateAccessToken(user);

        Claims claims = parse(token, SECRET);
        assertThat(claims.getSubject()).isEqualTo("alice@example.com");
        assertThat(claims.getIssuer()).isEqualTo("test-issuer");
        assertThat(claims.getId()).isNotBlank();
        assertThat(((Number) claims.get("userId")).longValue()).isEqualTo(42L);
        assertThat(claims.get("name", String.class)).isEqualTo("Test User");
        assertThat(claims.get("roles", List.class)).containsExactly("CUSTOMER");
        Date iat = claims.getIssuedAt();
        Date exp = claims.getExpiration();
        assertThat(exp.getTime() - iat.getTime()).isBetween(14 * 60 * 1000L, 16 * 60 * 1000L);
    }

    @Test
    @DisplayName("two tokens for the same user have different jti")
    void generateAccessToken_uniqueJti() {
        JwtUtils u = utils(SECRET, "");
        ReflectionTestUtils.invokeMethod(u, "init");
        User user = TestData.user(1L, "a@b.com", UserRole.ADMIN);

        Claims a = parse(u.generateAccessToken(user), SECRET);
        Claims b = parse(u.generateAccessToken(user), SECRET);

        assertThat(a.getId()).isNotEqualTo(b.getId());
    }

    @Test
    @DisplayName("legacy secret property is used when jwt.secret is empty")
    void init_legacySecretFallback() {
        JwtUtils u = utils("", SECRET);
        ReflectionTestUtils.invokeMethod(u, "init");

        String token = u.generateAccessToken(TestData.user(1L, "a@b.com", UserRole.CUSTOMER));

        assertThat(parse(token, SECRET).getSubject()).isEqualTo("a@b.com");
    }

    @Test
    @DisplayName("init fails when no secret is configured")
    void init_noSecret_fails() {
        JwtUtils u = utils(" ", null);

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(u, "init"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("init fails when the secret is shorter than 32 bytes")
    void init_shortSecret_fails() {
        JwtUtils u = utils("too-short", "");

        assertThatThrownBy(() -> ReflectionTestUtils.invokeMethod(u, "init"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
    }

    @Test
    @DisplayName("a 32 byte secret is accepted")
    void init_exactly32Bytes_ok() {
        JwtUtils u = utils("12345678901234567890123456789012", "");

        ReflectionTestUtils.invokeMethod(u, "init");

        assertThat(u.generateAccessToken(TestData.user(1L, "a@b.com", UserRole.CUSTOMER))).isNotBlank();
    }

    @Test
    @DisplayName("expiry in seconds is minutes * 60")
    void getAccessTokenExpirationSeconds() {
        assertThat(utils(SECRET, "").getAccessTokenExpirationSeconds()).isEqualTo(900L);
    }
}
