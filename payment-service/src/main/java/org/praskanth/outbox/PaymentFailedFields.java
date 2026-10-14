package org.praskanth.outbox;

public record PaymentFailedFields(Long orderId, String reason) {}
