package org.praskanth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.events.PaymentCompletedEvent;
import org.praskanth.events.PaymentFailedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private static final String COMPLETED_TOPIC = "payment.completed";
    private static final String FAILED_TOPIC = "payment.failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishPaymentCompleted(Long orderId, BigDecimal amount, String transactionId) {
        PaymentCompletedEvent event = PaymentCompletedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(orderId)
                .setAmount(toByteBuffer(amount))
                .setTransactionId(transactionId)
                .setCompletedAt(Instant.now())
                .build();

        kafkaTemplate.send(COMPLETED_TOPIC, String.valueOf(orderId), event)
                .whenComplete((result, ex) -> logResult(ex, "PaymentCompletedEvent", orderId, event.getEventId()));
    }

    public void publishPaymentFailed(Long orderId, String reason) {
        PaymentFailedEvent event = PaymentFailedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(orderId)
                .setReason(reason)
                .setFailedAt(Instant.now())
                .build();

        kafkaTemplate.send(FAILED_TOPIC, String.valueOf(orderId), event)
                .whenComplete((result, ex) -> logResult(ex, "PaymentFailedEvent", orderId, event.getEventId()));
    }

    private void logResult(Throwable ex, String eventType, Long orderId, String eventId) {
        if (ex != null) {
            log.error("Failed to publish {} for order {}", eventType, orderId, ex);
        } else {
            log.info("Published {} eventId={} for order {}", eventType, eventId, orderId);
        }
    }

    private ByteBuffer toByteBuffer(BigDecimal value) {
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return ByteBuffer.wrap(scaled.unscaledValue().toByteArray());
    }
}
