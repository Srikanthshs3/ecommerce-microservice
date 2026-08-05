package org.praskanth.exception;

/** Thrown for invalid client input that isn't caught by bean validation. Mapped to HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
