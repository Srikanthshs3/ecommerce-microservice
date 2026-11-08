package org.praskanth.outbox;

import java.math.BigDecimal;
import java.time.Instant;

/** method = RAZORPAY / STRIPE / COD (null on rows written before it existed: published as ONLINE). */
public record PaymentCompletedFields(Long orderId, BigDecimal amount, String transactionId, String method, Instant completedAt) {}
