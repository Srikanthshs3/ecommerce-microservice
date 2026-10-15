package org.praskanth.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Map;

/**
 * Checks a Google reCAPTCHA token (the "I'm not a robot" box) on the server.
 *
 * Configure with RECAPTCHA_SECRET (from .env). Without it the CAPTCHA step is switched off: the
 * attempt counter and the account block still work, but nobody is asked to solve anything.
 *
 * If Google cannot be reached we let the attempt through (and log it) rather than lock everybody out;
 * an explicit "not valid" answer from Google always fails the check.
 */
@Service
@Slf4j
public class CaptchaService {

    @Value("${security.captcha.secret:${RECAPTCHA_SECRET:}}")
    private String secret;

    @Value("${security.captcha.verify-url:https://www.google.com/recaptcha/api/siteverify}")
    private String verifyUrl;

    private final RestClient http;

    public CaptchaService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(4));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    @PostConstruct
    void announce() {
        if (isEnabled()) {
            log.info("reCAPTCHA is ON: required after repeated failed logins");
        } else {
            log.warn("reCAPTCHA is OFF (RECAPTCHA_SECRET not set). Failed logins are still counted and the account is still blocked.");
        }
    }

    public boolean isEnabled() {
        return secret != null && !secret.isBlank();
    }

    /** True when the token proves a human solved the challenge. */
    public boolean verify(String token) {
        if (!isEnabled()) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", secret.strip());
        form.add("response", token);
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> answer = http.post().uri(verifyUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
            return answer != null && Boolean.TRUE.equals(answer.get("success"));
        } catch (RestClientException e) {
            log.error("Could not reach the reCAPTCHA service ({}); letting this attempt through", e.getMessage());
            return true;
        }
    }
}
