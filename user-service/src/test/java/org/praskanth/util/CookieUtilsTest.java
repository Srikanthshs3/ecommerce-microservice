package org.praskanth.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilsTest {

    private CookieUtils cookieUtils;

    @BeforeEach
    void setUp() {
        cookieUtils = new CookieUtils();
        ReflectionTestUtils.setField(cookieUtils, "secure", true);
        ReflectionTestUtils.setField(cookieUtils, "path", "/api/v1/auth");
        ReflectionTestUtils.setField(cookieUtils, "refreshExpirationDays", 7);
    }

    @Test
    void createRefreshTokenCookie_usesConfiguredDefaults() {
        ResponseCookie cookie = cookieUtils.createRefreshTokenCookie("tok");

        assertThat(cookie.getName()).isEqualTo(CookieUtils.REFRESH_TOKEN_COOKIE_NAME);
        assertThat(cookie.getValue()).isEqualTo("tok");
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.isSecure()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofDays(7));
        assertThat(cookie.getSameSite()).isEqualTo("Lax");
    }

    @Test
    void createRefreshTokenCookie_insecureForLocalDev() {
        ReflectionTestUtils.setField(cookieUtils, "secure", false);

        assertThat(cookieUtils.createRefreshTokenCookie("tok").isSecure()).isFalse();
    }

    @Test
    void createRefreshTokenCookie_customMaxAge() {
        ResponseCookie cookie = cookieUtils.createRefreshTokenCookie("tok", Duration.ofMinutes(5));

        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ofMinutes(5));
    }

    @Test
    void deleteRefreshTokenCookie_hasEmptyValueAndZeroMaxAge() {
        ResponseCookie cookie = cookieUtils.deleteRefreshTokenCookie();

        assertThat(cookie.getValue()).isEmpty();
        assertThat(cookie.getMaxAge()).isEqualTo(Duration.ZERO);
        assertThat(cookie.isHttpOnly()).isTrue();
        assertThat(cookie.getPath()).isEqualTo("/api/v1/auth");
    }
}
