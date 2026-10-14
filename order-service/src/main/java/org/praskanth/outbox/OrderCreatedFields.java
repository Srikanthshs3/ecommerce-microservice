package org.praskanth.outbox;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Plain JSON shape stored in the outbox row's json_payload column. Deliberately NOT the Avro
 * OrderCreatedEvent itself - this is just a durable snapshot of the fields needed to rebuild
 * that Avro event later, in OrderOutboxPoller, at actual-publish time.
 */
public record OrderCreatedFields(
        Long orderId,
        Long userId,
        List<ItemField> items,
        BigDecimal totalAmount,
        Instant createdAt
) {
    public record ItemField(String productId, int quantity, BigDecimal unitPrice) {}
}
