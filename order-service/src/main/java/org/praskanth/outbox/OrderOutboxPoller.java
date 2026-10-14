package org.praskanth.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.events.OrderCreatedEvent;
import org.praskanth.events.OrderItemPayload;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

/**
 * Publishes outbox rows that OrderService wrote, outside the original business transaction.
 * Runs every 2s, picks up to 50 PENDING rows at a time, and publishes each one synchronously
 * (kafkaTemplate.send(...).get()) so a failure is caught right here rather than silently lost
 * in an async callback. A row that keeps failing is given up on after MAX_ATTEMPTS and marked
 * FAILED so it doesn't retry forever - a human/alert should look at FAILED rows, since they
 * mean a real order exists in the DB whose event was never delivered to Kafka.
 *
 * (OrderEventProducer is now dead code for the request path - this poller is the only place
 * that still builds a real OrderCreatedEvent, reusing the same Avro decimal-encoding convention
 * it used.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderOutboxPoller {

    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending =
                outboxEventRepository.findTop50ByStatusOrderByIdAsc(OutboxEvent.OutboxStatus.PENDING);

        for (OutboxEvent outboxEvent : pending) {
            try {
                OrderCreatedEvent event = toAvroEvent(outboxEvent);
                kafkaTemplate.send(outboxEvent.getTopic(), outboxEvent.getPartitionKey(), event).get();

                outboxEvent.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                outboxEvent.setPublishedAt(java.time.Instant.now());
                outboxEventRepository.save(outboxEvent);
                log.info("Published outbox event id={} type={} to topic={}",
                        outboxEvent.getId(), outboxEvent.getEventType(), outboxEvent.getTopic());
            } catch (Exception ex) {
                int attempts = outboxEvent.getAttemptCount() + 1;
                outboxEvent.setAttemptCount(attempts);
                outboxEvent.setLastError(ex.getMessage());
                if (attempts >= MAX_ATTEMPTS) {
                    outboxEvent.setStatus(OutboxEvent.OutboxStatus.FAILED);
                    log.error("Giving up on outbox event id={} after {} attempts", outboxEvent.getId(), attempts, ex);
                } else {
                    log.warn("Failed to publish outbox event id={} (attempt {}/{}): {}",
                            outboxEvent.getId(), attempts, MAX_ATTEMPTS, ex.getMessage());
                }
                outboxEventRepository.save(outboxEvent);
            }
        }
    }

    private OrderCreatedEvent toAvroEvent(OutboxEvent outboxEvent) throws Exception {
        OrderCreatedFields fields = objectMapper.readValue(outboxEvent.getJsonPayload(), OrderCreatedFields.class);

        List<OrderItemPayload> items = fields.items().stream()
                .map(i -> OrderItemPayload.newBuilder()
                        .setProductId(i.productId())
                        .setQuantity(i.quantity())
                        .setUnitPrice(toByteBuffer(i.unitPrice()))
                        .build())
                .toList();

        return OrderCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(fields.orderId())
                .setUserId(fields.userId())
                .setItems(items)
                .setTotalAmount(toByteBuffer(fields.totalAmount()))
                .setCreatedAt(fields.createdAt())
                .build();
    }

    private ByteBuffer toByteBuffer(BigDecimal value) {
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return ByteBuffer.wrap(scaled.unscaledValue().toByteArray());
    }
}
