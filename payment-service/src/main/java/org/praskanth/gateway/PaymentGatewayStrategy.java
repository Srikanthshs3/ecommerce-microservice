package org.praskanth.gateway;

import org.praskanth.entity.PaymentGateway;

import java.math.BigDecimal;
import java.util.Map;

/**
 * One implementation per online gateway (Strategy pattern). PaymentService only talks to this interface, so adding a
 * gateway means adding one class. Card data never touches our servers: the browser pays directly with the gateway
 * (Razorpay Checkout / Stripe Elements) and we only create the order up front and VERIFY the result afterwards.
 */
public interface PaymentGatewayStrategy {

    PaymentGateway getGateway();

    /** False when keys are missing, or are live keys while sandbox-only mode is on. The method is then hidden from customers. */
    boolean isAvailable();

    /** Creates the gateway-side order / PaymentIntent. Returns its id. */
    String createOrder(String checkoutRef, BigDecimal amount, String currency) throws Exception;

    /** What the browser needs to open the payment form (public values only, never a secret key). */
    Map<String, Object> clientConfig(String gatewayOrderId, BigDecimal amount, String currency) throws Exception;

    /** Checks the proof the browser sends back. Throws PaymentVerificationException when it does not hold. */
    VerifiedPayment verify(String gatewayOrderId, BigDecimal expectedAmount, Map<String, String> proof) throws Exception;

    /** Returns the gateway's refund id. {@code idempotencyKey} makes a retry safe. */
    String refund(String gatewayPaymentId, BigDecimal amount, String idempotencyKey) throws Exception;

    /** Best effort: close an unpaid gateway order. */
    void cancelOrder(String gatewayOrderId);

    /** Verifies the webhook signature and turns the payload into a neutral event. */
    WebhookEvent parseWebhook(String payload, String signature) throws Exception;
}
