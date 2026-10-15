package org.praskanth.exception;

/** The caller is authenticated but not allowed to perform this operation (maps to HTTP 403). */
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
