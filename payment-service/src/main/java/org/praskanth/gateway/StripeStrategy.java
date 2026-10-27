package org.praskanth.gateway;

import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.entity.PaymentGateway;
import org.praskanth.exception.PaymentVerificationException;
import org.praskanth.util.Money;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Stripe in TEST mode: a PaymentIntent is created here; the browser confirms it with Stripe Elements (the customer types
 * a test card such as 4242 4242 4242 4242 into Stripe's own form); we then retrieve the PaymentIntent and check it.
 */
@Component
@Slf4j
public class StripeStrategy implements PaymentGatewayStrategy {

    private static final Pattern TEST_KEY = Pattern.compile("^sk_test_[A-Za-z0-9]{16,}$");
    private static final Pattern ANY_KEY = Pattern.compile("^sk_(test|live)_[A-Za-z0-9]{16,}$");
    private static final Set<String> CANCELLABLE =
            Set.of("requires_payment_method", "requires_confirmation", "requires_action");

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Value("${stripe.publishable-key:}")
    private String publishableKey;

    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Value("${payment.sandbox-only:true}")
    private boolean sandboxOnly;

    @Override
    public PaymentGateway getGateway() {
        return PaymentGateway.STRIPE;
    }

    @Override
    public boolean isAvailable() {
        if (secretKey == null || publishableKey == null || !publishableKey.startsWith("pk_")) {
            return false;
        }
        return (sandboxOnly ? TEST_KEY : ANY_KEY).matcher(secretKey).matches();
    }

    private RequestOptions options() {
        if (!isAvailable()) {
            throw new IllegalStateException("Stripe is not configured (STRIPE_SECRET_KEY / STRIPE_PUBLISHABLE_KEY, test keys only)");
        }
        return RequestOptions.builder().setApiKey(secretKey).build();
    }

    @Override
    public String createOrder(String checkoutRef, BigDecimal amount, String currency) throws Exception {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(Money.toMinor(amount))
                .setCurrency(currency.toLowerCase())
                .putMetadata("checkoutRef", checkoutRef)
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder().setEnabled(true).build())
                .build();
        PaymentIntent intent = PaymentIntent.create(params, options());
        log.info("Created Stripe PaymentIntent {} for checkout {}", intent.getId(), checkoutRef);
        return intent.getId();
    }

    @Override
    public Map<String, Object> clientConfig(String gatewayOrderId, BigDecimal amount, String currency) throws Exception {
        PaymentIntent intent = PaymentIntent.retrieve(gatewayOrderId, options());
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("publishableKey", publishableKey);
        config.put("clientSecret", intent.getClientSecret());
        config.put("amount", Money.toMinor(amount));
        config.put("currency", currency);
        return config;
    }

    @Override
    public VerifiedPayment verify(String gatewayOrderId, BigDecimal expectedAmount, Map<String, String> proof) throws Exception {
        String intentId = proof.get("paymentIntentId");
        if (intentId == null || intentId.isBlank()) {
            throw new PaymentVerificationException("Payment details are missing. Please try again.");
        }
        if (!intentId.equals(gatewayOrderId)) {
            throw new PaymentVerificationException("This payment does not belong to this order.");
        }
        // Never trust the browser: ask Stripe what really happened.
        PaymentIntent intent = PaymentIntent.retrieve(intentId, options());
        if (!"succeeded".equals(intent.getStatus())) {
            throw new PaymentVerificationException("The payment has not been completed yet. Please finish the payment or try again.");
        }
        Long received = intent.getAmountReceived();
        if (received == null || received != Money.toMinor(expectedAmount)) {
            throw new PaymentVerificationException("The amount paid does not match your order total, so the payment was not accepted. Please contact support.");
        }
        return new VerifiedPayment(intent.getId());
    }

    @Override
    public String refund(String gatewayPaymentId, BigDecimal amount, String idempotencyKey) throws Exception {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(gatewayPaymentId)
                .setAmount(Money.toMinor(amount))
                .build();
        RequestOptions withKey = RequestOptions.builder().setApiKey(secretKey).setIdempotencyKey(idempotencyKey).build();
        Refund refund = Refund.create(params, withKey);
        log.info("Stripe refund {} of {} for {}", refund.getId(), amount, gatewayPaymentId);
        return refund.getId();
    }

    @Override
    public void cancelOrder(String gatewayOrderId) {
        try {
            PaymentIntent intent = PaymentIntent.retrieve(gatewayOrderId, options());
            if (CANCELLABLE.contains(intent.getStatus())) {
                intent.cancel(options());
            }
        } catch (StripeException | RuntimeException ex) {
            log.warn("Could not cancel Stripe PaymentIntent {}: {}", gatewayOrderId, ex.getMessage());
        }
    }

    @Override
    public WebhookEvent parseWebhook(String payload, String signature) throws Exception {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new PaymentVerificationException("Stripe webhook secret is not configured.");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (com.stripe.exception.SignatureVerificationException ex) {
            throw new PaymentVerificationException("Invalid Stripe webhook signature.");
        }
        String type = event.getType();
        if (!"payment_intent.succeeded".equals(type) && !"payment_intent.payment_failed".equals(type)) {
            return WebhookEvent.ignored();
        }
        var deserializer = event.getDataObjectDeserializer();
        StripeObject object = deserializer.getObject().isPresent()
                ? deserializer.getObject().get()
                : deserializer.deserializeUnsafe();
        if (!(object instanceof PaymentIntent intent)) {
            return WebhookEvent.ignored();
        }
        if ("payment_intent.succeeded".equals(type)) {
            return new WebhookEvent(WebhookEvent.Type.SUCCEEDED, intent.getId(), intent.getId(), null);
        }
        String reason = intent.getLastPaymentError() == null ? "Payment failed" : intent.getLastPaymentError().getMessage();
        return new WebhookEvent(WebhookEvent.Type.FAILED, intent.getId(), intent.getId(), reason);
    }
}
