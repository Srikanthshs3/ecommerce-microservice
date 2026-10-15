package org.praskanth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.JwtParserBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The gateway only ever CONSUMES tokens issued by user-service - it never issues them.
 * This is the single place in the whole platform where a JWT signature is verified.
 *
 * The token is parsed exactly once per request (signature + expiry are enforced by the parser) and the
 * result is converted into an {@link Identity}. Anything that does not yield a complete, well-formed
 * identity is treated as an invalid token.
 */
@Component
@Slf4j
public class JwtUtils {

    /** Role names become a comma separated header value, so they must never contain separators/whitespace. */
    private static final Pattern SAFE_ROLE = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");
    private static final int MAX_EMAIL_LENGTH = 320;

    private SecretKey key;
    private JwtParser parser;

    /** Same property as user-service (jwt.secret / JWT_SECRET). */
    @Value("${jwt.secret:}")
    private String secret;

    /** Legacy property name, still honoured so an existing config-server entry keeps working. */
    @Value("${secreteJwtString:}")
    private String legacySecret;

    /** When set, tokens must carry this issuer (user-service sets it). */
    @Value("${jwt.issuer:}")
    private String issuer;

    /** What downstream services are allowed to know about the caller. */
    public record Identity(long userId, String email, List<String> roles) {
    }

    @PostConstruct
    private void init() {
        String effective = StringUtils.hasText(secret) ? secret : legacySecret;
        if (!StringUtils.hasText(effective)) {
            // fail closed: never start with a built-in default secret
            throw new IllegalStateException("JWT secret is not configured (set jwt.secret / JWT_SECRET, "
                    + "the SAME value user-service signs with)");
        }
        byte[] bytes = effective.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes long");
        }
        // Keys.hmacShaKeyFor == what user-service uses to sign, so HS256/384/512 selection (by key length) matches.
        this.key = Keys.hmacShaKeyFor(bytes);
        JwtParserBuilder builder = Jwts.parser().verifyWith(key);
        if (StringUtils.hasText(issuer)) {
            builder.requireIssuer(issuer);
        }
        this.parser = builder.build();
    }

    /**
     * @return the verified identity, or empty if the token is malformed, tampered with, expired,
     *         or lacks the claims downstream services depend on (userId, subject).
     */
    public Optional<Identity> authenticate(String token) {
        try {
            // verifyWith() => signature checked; parseSignedClaims() => unsigned/"alg":"none" tokens rejected;
            // expiry (exp) is enforced by the parser itself.
            Claims claims = parser.parseSignedClaims(token).getPayload();
            return toIdentity(claims);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT rejected: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Identity> toIdentity(Claims claims) {
        Long userId = toLong(claims.get("userId"));
        String email = claims.getSubject();

        if (userId == null || email == null || email.isBlank()
                || email.length() > MAX_EMAIL_LENGTH
                || email.indexOf('\n') >= 0 || email.indexOf('\r') >= 0) {
            log.warn("JWT rejected: missing/invalid userId or subject claim");
            return Optional.empty();
        }

        List<String> roles = new ArrayList<>();
        if (claims.get("roles") instanceof List<?> raw) {
            for (Object r : raw) {
                if (r instanceof String s && SAFE_ROLE.matcher(s).matches()) {
                    roles.add(s);
                } else {
                    log.warn("Dropping malformed role claim value from token of userId={}", userId);
                }
            }
        }
        return Optional.of(new Identity(userId, email, List.copyOf(roles)));
    }

    private static Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
