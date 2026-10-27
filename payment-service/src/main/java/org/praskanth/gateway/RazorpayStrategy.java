package org.praskanth.gateway;

import com.razorpay.RazorpayClient;
import com.razorpay.Utils;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.praskanth.entity.PaymentGateway;
import org.praskanth.exception.PaymentVerificationException;
import org.praskanth.util.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Razorpay in TEST mode (rzp_test_ keys): Checkout in the browser, signature + amount verified here. */
@Component
@Slf4j
public class RazorpayStrategy implements PaymentGatewayStrategy {

    @Value("${razorpay.key-id:}")
    private String keyId;

    @Value("${razorpay.key-secret:}")
    private String keySecret;

    @Value("${razorpay.webhook-secret:}")
    private String webhookSecret;

    /** Learning project: refuse live keys so real money can never move. */
    @Value("${payment.sandbox-only:true}")
    private boolean sandboxOnly;

    private RazorpayClient client;

    @Override
    public PaymentGateway getGateway() {
        return PaymentGateway.RAZORPAY;
    }

    @Override
    public boolean isAvailable() {
        if (keyId == null || keySecret == null || keySecret.isBlank()) {
            return false;
        }
        return sandboxOnly ? keyId.startsWith("rzp_test_") : keyId.startsWith("rzp_");
    }

    private synchronized RazorpayClient client() throws Exception {
        if (!isAvailable()) {
            throw new IllegalStateException("Razorpay is not configured (RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET, test keys only)");
        }
        if (client == null) {
            client = new RazorpayClient(keyId, keySecret);
        }
        return client;
    }

    @Override
    public String createOrder(String checkoutRef, BigDecimal amount, String currency) throws Exception {
        JSONObject request = new JSONObject();
        request.put("amount", Money.toMinor(amount));
        request.put("currency", currency);
        request.put("receipt", checkoutRef); // max 40 chars; a UUID is 36
        request.put("payment_capture", 1);
        JSONObject notes = new JSONObject();
        notes.put("checkoutRef", checkoutRef);
        request.put("notes", notes);

        com.razorpay.Order order = client().orders.create(request);
        String id = order.get("id");
        log.info("Created Razorpay order {} for checkout {}", id, checkoutRef);
        return id;
    }

    @Override
    public Map<String, Object> clientConfig(String gatewayOrderId, BigDecimal amount, String currency) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("keyId", keyId);
        config.put("orderId", gatewayOrderId);
        config.put("amount", Money.toMinor(amount));
        config.put("currency", currency);
        config.put("name", "eShop");
        config.put("description", "Order payment (test mode)");
        return config;
    }

    @Override
    public VerifiedPayment verify(String gatewayOrderId, BigDecimal expectedAmount, Map<String, String> proof) throws Exception {
        String orderId = proof.get("razorpayOrderId");
        String paymentId = proof.get("razorpayPaymentId");
        String signature = proof.get("razorpaySignature");
        if (isBlank(orderId) || isBlank(paymentId) || isBlank(signature)) {
            throw new PaymentVerificationException("Payment details are missing. Please try again.");
        }
        if (!orderId.equals(gatewayOrderId)) {
            throw new PaymentVerificationException("This payment does not belong to this order.");
        }

        JSONObject attributes = new JSONObject();
        attributes.put("razorpay_order_id", orderId);
        attributes.put("razorpay_payment_id", paymentId);
        attributes.put("razorpay_signature", signature);
        if (!Utils.verifyPaymentSignature(attributes, keySecret)) {
            throw new PaymentVerificationException("We couldn't verify this payment, so it was not accepted. Please try again or choose another payment method.");
        }

        // The signature proves Razorpay issued this payment for this order; also check what was really paid.
        com.razorpay.Payment payment = client().payments.fetch(paymentId);
        Number paid = payment.get("amount");
        String status = payment.get("status");
        if (paid == null || paid.longValue() != Money.toMinor(expectedAmount)) {
            throw new PaymentVerificationException("The amount paid does not match your order total, so the payment was not accepted. Please contact support.");
        }
        if (!"captured".equals(status) && !"authorized".equals(status)) {
            throw new PaymentVerificationException("The payment has not been completed yet. Please finish the payment or try again.");
        }
        return new VerifiedPayment(paymentId);
    }

    @Override
    public String refund(String gatewayPaymentId, BigDecimal amount, String idempotencyKey) throws Exception {
        JSONObject request = new JSONObject();
        request.put("amount", Money.toMinor(amount));
        JSONObject notes = new JSONObject();
        notes.put("key", idempotencyKey);
        request.put("notes", notes);
        com.razorpay.Refund refund = client().payments.refund(gatewayPaymentId, request);
        String id = refund.get("id");
        log.info("Razorpay refund {} of {} for payment {}", id, amount, gatewayPaymentId);
        return id;
    }

    @Override
    public void cancelOrder(String gatewayOrderId) {
        // Razorpay orders cannot be cancelled; an unpaid one simply stays unused.
    }

    @Override
    public WebhookEvent parseWebhook(String payload, String signature) throws Exception {
        if (isBlank(webhookSecret)) {
            throw new PaymentVerificationException("Razorpay webhook secret is not configured.");
        }
        if (isBlank(signature) || !Utils.verifyWebhookSignature(payload, signature, webhookSecret)) {
            throw new PaymentVerificationException("Invalid Razorpay webhook signature.");
        }
        JSONObject root = new JSONObject(payload);
        String event = root.optString("event");
        JSONObject body = root.optJSONObject("payload");
        JSONObject paymentWrapper = body == null ? null : body.optJSONObject("payment");
        JSONObject entity = paymentWrapper == null ? null : paymentWrapper.optJSONObject("entity");
        if (entity == null) {
            return WebhookEvent.ignored();
        }
        String orderId = entity.optString("order_id", null);
        String paymentId = entity.optString("id", null);
        return switch (event) {
            case "payment.captured", "order.paid" ->
                    new WebhookEvent(WebhookEvent.Type.SUCCEEDED, orderId, paymentId, null);
            case "payment.failed" ->
                    new WebhookEvent(WebhookEvent.Type.FAILED, orderId, paymentId, entity.optString("error_description", "Payment failed"));
            default -> WebhookEvent.ignored();
        };
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
