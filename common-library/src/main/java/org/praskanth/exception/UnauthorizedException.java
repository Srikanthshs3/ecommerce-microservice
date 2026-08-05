package org.praskanth.exception;

/** Thrown for authentication failures (bad credentials, invalid/expired token). Mapped to HTTP 401. */
public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
}
