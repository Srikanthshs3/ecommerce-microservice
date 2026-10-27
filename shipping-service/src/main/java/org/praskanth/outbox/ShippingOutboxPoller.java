package org.praskanth.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.events.ShipmentStatusChangedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Publishes the outbox rows ShippingService wrote (same retry / give-up bookkeeping as the other services' pollers). */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShippingOutboxPoller {

    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findTop50ByStatusOrderByIdAsc(OutboxEvent.OutboxStatus.PENDING);
        for (OutboxEvent outboxEvent : pending) {
            try {
                kafkaTemplate.send(outboxEvent.getTopic(), outboxEvent.getPartitionKey(), toAvroEvent(outboxEvent)).get();
                outboxEvent.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                outboxEvent.setPublishedAt(Instant.now());
                outboxEventRepository.save(outboxEvent);
                log.info("Published outbox event id={} type={} to topic={}", outboxEvent.getId(), outboxEvent.getEventType(), outboxEvent.getTopic());
            } catch (Exception ex) {
                int attempts = outboxEvent.getAttemptCount() + 1;
                outboxEvent.setAttemptCount(attempts);
                outboxEvent.setLastError(ex.getMessage());
                if (attempts >= MAX_ATTEMPTS) {
                    outboxEvent.setStatus(OutboxEvent.OutboxStatus.FAILED);
                    log.error("Giving up on outbox event id={} after {} attempts", outboxEvent.getId(), attempts, ex);
                } else {
                    log.warn("Failed to publish outbox event id={} (attempt {}/{}): {}", outboxEvent.getId(), attempts, MAX_ATTEMPTS, ex.getMessage());
                }
                outboxEventRepository.save(outboxEvent);
            }
        }
    }

    private Object toAvroEvent(OutboxEvent outboxEvent) throws Exception {
        if (!"ShipmentStatusChangedEvent".equals(outboxEvent.getEventType())) {
            throw new IllegalStateException("Unknown outbox event type: " + outboxEvent.getEventType());
        }
        ShipmentStatusFields f = objectMapper.readValue(outboxEvent.getJsonPayload(), ShipmentStatusFields.class);
        return ShipmentStatusChangedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(f.orderId())
                .setShipmentId(f.shipmentId())
                .setStatus(f.status())
                .setNote(f.note() == null ? "" : f.note())
                .setActor(f.actor() == null ? "" : f.actor())
                .setOccurredAt(f.occurredAt())
                .build();
    }
}
