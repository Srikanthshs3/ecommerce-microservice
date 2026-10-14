package org.praskanth.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class CookieUtils {
    public static final String REFRESH_TOKEN_COOKIE_NAME = "refreshToken";

    /** Set app.cookie.secure=false only for local http development. */
    @Value("${app.cookie.secure:true}")
    private boolean secure;

    /**
     * Only the auth endpoints (/refresh, /logout) need this cookie. Scoping it means the browser no longer sends it
     * on every cart/product/order call (less exposure, smaller requests).
     */
    @Value("${app.cookie.path:/api/v1/auth}")
    private String path;

    @Value("${jwt.refresh-expiration-days:7}")
    private int refreshExpirationDays;

    public ResponseCookie createRefreshTokenCookie(String token) {
        return createRefreshTokenCookie(token, Duration.ofDays(refreshExpirationDays));
    }

    public ResponseCookie createRefreshTokenCookie(String token, Duration maxAge) {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .maxAge(maxAge)
                .sameSite("Lax")
                .build();
    }

    public ResponseCookie deleteRefreshTokenCookie() {
        return ResponseCookie.from(REFRESH_TOKEN_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(secure)
                .path(path)
                .maxAge(0)
                .sameSite("Lax")
                .build();
    }
}
