package org.praskanth.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request/response shapes of the payment API, grouped in one file. */
public final class PaymentDtos {
    private PaymentDtos() {}

    public record MethodInfo(String code, String label, boolean enabled, String note) {}

    public record InitiateRequest(org.praskanth.entity.PaymentMethod method) {}

    /** Proof sent by the browser after paying; only the fields of the chosen gateway are filled. */
    public record ConfirmRequest(String razorpayOrderId, String razorpayPaymentId, String razorpaySignature,
                                 String paymentIntentId) {}

    public record FailureRequest(String reason) {}

    public record OrderLine(Long orderId, String status, BigDecimal amount) {}

    public record PaymentInfo(String method, String status, int attempts, String failureReason) {}

    /**
     * state: RESERVING (stock being reserved) | READY_TO_PAY | PAID | COD_PLACED | EXPIRED | CANCELLED | REFUNDED.
     */
    public record CheckoutSummary(String checkoutRef, BigDecimal amount, String currency, String state,
                                  Instant expiresAt, List<OrderLine> orders, PaymentInfo payment, String message) {}

    /** gateway = what the browser needs to open Razorpay Checkout / Stripe Elements (null for COD). */
    public record InitiateResponse(String checkoutRef, String method, String status, BigDecimal amount,
                                   String currency, Map<String, Object> gateway) {}

    public record OrderPaymentView(Long orderId, String checkoutRef, String method, String status,
                                   BigDecimal amount, String transactionId) {}
}
