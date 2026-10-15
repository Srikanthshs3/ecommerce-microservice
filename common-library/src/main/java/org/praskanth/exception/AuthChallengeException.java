package org.praskanth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * A login-flow answer that carries its own machine-readable code, so the web app can react to it:
 *   CAPTCHA_REQUIRED  show the "I'm not a robot" check and send its token with the next attempt
 *   ACCOUNT_LOCKED    the account is blocked; only an administrator can unblock it
 * Registered with noRollbackFor on AuthServiceImpl.login so that locking the account is really saved.
 */
@Getter
public class AuthChallengeException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public AuthChallengeException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

}
