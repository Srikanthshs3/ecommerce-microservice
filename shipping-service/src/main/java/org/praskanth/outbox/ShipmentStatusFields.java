package org.praskanth.outbox;

import java.time.Instant;

/** Outbox JSON snapshot for ShipmentStatusChangedEvent. */
public record ShipmentStatusFields(Long orderId, Long shipmentId, String status, String note, String actor, Instant occurredAt) {}
