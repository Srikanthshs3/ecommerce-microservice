package org.praskanth.outbox;

import java.time.Instant;
import java.util.List;

/** JSON snapshot stored in the outbox row for an OrderNotificationEvent (see OrderOutboxPoller). */
public record OrderNotificationFields(
        Long orderId,
        Long userId,
        String stage,
        String customerEmail,
        String customerName,
        String recipientName,
        String shippingAddress,
        String paymentMethod,
        String reason,
        String totalAmount,
        List<ItemField> items,
        Instant occurredAt
) {
    public record ItemField(String productName, String variantLabel, int quantity, String unitPrice) {}
}
