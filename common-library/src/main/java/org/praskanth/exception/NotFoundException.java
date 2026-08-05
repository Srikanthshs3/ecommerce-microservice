package org.praskanth.exception;

/** Thrown when a requested resource does not exist. Mapped to HTTP 404 by each service's exception handler. */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
