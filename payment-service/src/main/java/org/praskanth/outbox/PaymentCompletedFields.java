package org.praskanth.outbox;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentCompletedFields(Long orderId, BigDecimal amount, String transactionId, Instant completedAt) {}
