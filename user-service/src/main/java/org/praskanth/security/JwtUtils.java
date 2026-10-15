package org.praskanth.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues short-lived access tokens. It does NOT verify them: tokens are verified in exactly one place, the
 * API gateway (same jwt.secret), which forwards the identity as X-User-* headers.
 * Claim names (sub=email, userId, name, roles) are the contract the gateway reads; iss and jti are added.
 */
@Component
@Slf4j
public class JwtUtils {

    @Value("${jwt.secret:}")
    private String secret;

    /** Legacy property name, still honoured so an existing config-server entry keeps working. */
    @Value("${secreteJwtString:}")
    private String legacySecret;

    @Value("${jwt.access-expiration-minutes:15}")
    private long accessExpirationMinutes;

    @Value("${jwt.issuer:ecommerce-user-service}")
    private String issuer;

    private SecretKey key;

    @PostConstruct
    void init() {
        String effective = StringUtils.hasText(secret) ? secret : legacySecret;
        if (!StringUtils.hasText(effective)) {
            throw new IllegalStateException("JWT secret is not configured (set jwt.secret / JWT_SECRET)");
        }
        byte[] bytes = effective.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes long");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getRole().stream().map(r -> r.getName().name()).toList();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(issuer)
                .subject(user.getEmail())
                .claim("userId", user.getId())
                .claim("name", user.getName())
                .claim("roles", roles)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofMinutes(accessExpirationMinutes))))
                .signWith(key)
                .compact();
    }

    public long getAccessTokenExpirationSeconds() {
        return Duration.ofMinutes(accessExpirationMinutes).toSeconds();
    }
}
