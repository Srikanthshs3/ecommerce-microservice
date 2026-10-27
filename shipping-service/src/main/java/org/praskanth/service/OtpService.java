package org.praskanth.service;

import org.praskanth.entity.Shipment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The delivery code (OTP) the customer reads out to the agent at the door.
 *
 * It is NOT stored: it is HMAC-SHA256(secret, orderId:otpVersion) reduced to 6 digits, so it can be recomputed for the
 * customer's screen and for the check, and a database leak reveals no codes. A new pickup bumps otpVersion, which makes
 * the previous attempt's code useless. The secret never leaves the server.
 */
@Component
public class OtpService {

    @Value("${shipping.otp-secret:}")
    private String otpSecret;

    /** Fallback so local setups work without extra configuration; set SHIPPING_OTP_SECRET in real environments. */
    @Value("${JWT_SECRET:}")
    private String fallbackSecret;

    public String codeFor(Shipment shipment) {
        String secret = otpSecret != null && !otpSecret.isBlank() ? otpSecret : fallbackSecret;
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("Delivery codes need SHIPPING_OTP_SECRET (or JWT_SECRET) to be set.");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] h = mac.doFinal((shipment.getOrderId() + ":" + shipment.getOtpVersion()).getBytes(StandardCharsets.UTF_8));
            int n = ((h[0] & 0x7f) << 24) | ((h[1] & 0xff) << 16) | ((h[2] & 0xff) << 8) | (h[3] & 0xff);
            return String.format("%06d", n % 1_000_000);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not compute the delivery code", ex);
        }
    }

    /** Constant-time comparison (no timing hints about how many digits were right). */
    public boolean matches(Shipment shipment, String typed) {
        if (typed == null) {
            return false;
        }
        return MessageDigest.isEqual(codeFor(shipment).getBytes(StandardCharsets.UTF_8),
                typed.trim().getBytes(StandardCharsets.UTF_8));
    }
}
