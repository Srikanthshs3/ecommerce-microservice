package org.praskanth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

/**
 * The gateway only ever CONSUMES tokens issued by user-service - it never issues them.
 * Signing key must be identical to user-service's secreteJwtString or signature checks fail.
 */
@Component
@Slf4j
public class JwtUtils {

    private SecretKey key;

    @Value("${secreteJwtString}")
    private String secreteJwtString;

    @PostConstruct
    private void init() {
        byte[] keyBytes = secreteJwtString.getBytes(StandardCharsets.UTF_8);
        this.key = new SecretKeySpec(keyBytes, "HmacSHA256");
    }

    public String getUsernameFromToken(String token) {
        return extractClaims(token, Claims::getSubject);
    }

    /** Optional role claim - only populated if user-service adds it to the token. Returns empty list otherwise. */
    @SuppressWarnings("unchecked")
    public List<String> getRolesFromToken(String token) {
        Object roles = extractClaims(token, claims -> claims.get("roles"));
        if (roles instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }

    private <T> T extractClaims(String token, Function<Claims, T> claimsResolver) {
        Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token, Claims::getExpiration).before(new Date());
    }
}
