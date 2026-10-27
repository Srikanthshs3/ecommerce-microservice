package org.praskanth.exception;

/** Razorpay / Stripe could not be reached or refused the call. HTTP 503: the caller may simply try again. */
public class PaymentGatewayException extends ServiceUnavailableException {
    public PaymentGatewayException(String message, Throwable cause) {
        super(message, cause);
    }
}
