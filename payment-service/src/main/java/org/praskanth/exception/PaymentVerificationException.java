package org.praskanth.exception;

/** The proof the browser (or a webhook) sent does not check out: wrong signature, wrong amount, payment not completed. HTTP 400. */
public class PaymentVerificationException extends BadRequestException {
    public PaymentVerificationException(String message) {
        super(message);
    }
}
