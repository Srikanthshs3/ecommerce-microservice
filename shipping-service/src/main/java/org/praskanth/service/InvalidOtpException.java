package org.praskanth.service;

import org.praskanth.exception.BadRequestException;

/** Wrong delivery code (HTTP 400). Not rolled back, so the failed attempt is still counted. */
public class InvalidOtpException extends BadRequestException {
    public InvalidOtpException(String message) {
        super(message);
    }
}
