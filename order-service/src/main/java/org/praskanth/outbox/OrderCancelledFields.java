package org.praskanth.outbox;

import java.time.Instant;

/** Outbox JSON snapshot for OrderCancelledEvent (see OrderOutboxPoller). */
public record OrderCancelledFields(Long orderId, Long userId, String reason, Instant cancelledAt) {}
