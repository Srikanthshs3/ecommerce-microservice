package org.praskanth.gateway;

/** Gateway-neutral view of a webhook call. */
public record WebhookEvent(Type type, String gatewayOrderId, String gatewayPaymentId, String reason) {
    public enum Type { SUCCEEDED, FAILED, IGNORED }

    public static WebhookEvent ignored() {
        return new WebhookEvent(Type.IGNORED, null, null, null);
    }
}
