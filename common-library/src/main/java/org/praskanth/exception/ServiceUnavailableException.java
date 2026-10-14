package org.praskanth.exception;

/**
 * A service this one depends on is down, slow, or its circuit breaker is open. Mapped to HTTP 503.
 * Use this (not a bare RuntimeException, which would surface as a 500) in resilience fallbacks.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
