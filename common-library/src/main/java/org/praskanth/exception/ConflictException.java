package org.praskanth.exception;

/** Thrown on state conflicts, e.g. duplicate email on signup or insufficient stock. Mapped to HTTP 409. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
