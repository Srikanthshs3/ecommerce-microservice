package org.praskanth.gateway;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.praskanth.entity.PaymentGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Razorpay gateway.
 *
 * Where the keys come from (nothing is stored in the repository):
 *   - In Docker:  docker-compose.yml passes RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET from your local .env.
 *   - Locally (IntelliJ / mvn spring-boot:run): application.yaml imports ./.env or ../.env, so the same two
 *     variables are read straight from that file.
 * The properties default to empty so the service still starts without keys (for example when only STRIPE is
 * used). A payment attempt without valid keys fails with a clear message instead of a stack trace.
 */
@Component
@Slf4j
public class RazorpayStrategy implements PaymentGatewayStrategy {

    @Value("${razorpay.key-id:}")
    private String keyId;

    @Value("${razorpay.key-secret:}")
    private String keySecret;

    private volatile RazorpayClient client;

    @PostConstruct
    void checkConfiguration() {
        if (isBlank(keyId) || isBlank(keySecret)) {
            log.warn("Razorpay keys are not configured (RAZORPAY_KEY_ID / RAZORPAY_KEY_SECRET). "
                    + "Razorpay payments will fail until they are set in .env.");
        } else if (!keyId.strip().startsWith("rzp_")) {
            log.warn("RAZORPAY_KEY_ID does not look like a Razorpay key id (expected rzp_test_... or rzp_live_...).");
        } else {
            log.info("Razorpay configured with key id {}...", keyId.strip().substring(0, Math.min(12, keyId.strip().length())));
        }
    }

    @Override
    public PaymentGateway getGateway() {
        return PaymentGateway.RAZORPAY;
    }

    @Override
    public String executePayment(Long orderId, BigDecimal amount, String currency) throws Exception {
        RazorpayClient razorpay = client();

        JSONObject orderRequest = new JSONObject();
        // Razorpay wants the amount in the smallest unit (paise) as an integer. Round instead of truncating.
        orderRequest.put("amount", amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact());
        orderRequest.put("currency", currency != null ? currency : "INR");
        orderRequest.put("receipt", "rcpt_order_" + orderId);

        Order razorpayOrder = razorpay.orders.create(orderRequest);
        String rzpOrderId = razorpayOrder.get("id");
        log.info("Created Razorpay order {} for platform order {}", rzpOrderId, orderId);

        // For automated backend processing, return transaction reference
        return "rzp_txn_" + rzpOrderId.replace("order_", "") + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    /** Builds the Razorpay client once, and fails with an actionable message when the keys are missing or wrong. */
    private RazorpayClient client() throws Exception {
        RazorpayClient local = client;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (client == null) {
                if (isBlank(keyId) || isBlank(keySecret)) {
                    throw new IllegalStateException(
                            "Razorpay is not configured: set RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET in your .env "
                                    + "(Razorpay dashboard > Test Mode > API Keys) and restart payment-service.");
                }
                if (!keyId.strip().startsWith("rzp_")) {
                    throw new IllegalStateException(
                            "RAZORPAY_KEY_ID is not a valid Razorpay key id (expected rzp_test_... or rzp_live_...). "
                                    + "Check the value in your .env.");
                }
                client = new RazorpayClient(keyId.strip(), keySecret.strip());
            }
            return client;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
