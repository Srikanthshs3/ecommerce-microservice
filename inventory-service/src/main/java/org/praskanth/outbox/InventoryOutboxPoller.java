package org.praskanth.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.events.InventoryReservationFailedEvent;
import org.praskanth.events.InventoryReservedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publishes outbox rows written by InventoryService.reserveForOrder - both the success
 * (InventoryReservedEvent) and failure (InventoryReservationFailedEvent) outcomes go through
 * the same outbox table, distinguished by event_type, so this poller switches on that column
 * to decide which Avro record to rebuild. Same retry/give-up bookkeeping as OrderOutboxPoller.
 * (InventoryEventProducer is now dead code for these two event types.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryOutboxPoller {

    private static final int MAX_ATTEMPTS = 10;

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending =
                outboxEventRepository.findTop50ByStatusOrderByIdAsc(OutboxEvent.OutboxStatus.PENDING);

        for (OutboxEvent outboxEvent : pending) {
            try {
                Object event = toAvroEvent(outboxEvent);
                kafkaTemplate.send(outboxEvent.getTopic(), outboxEvent.getPartitionKey(), event).get();

                outboxEvent.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
                outboxEvent.setPublishedAt(Instant.now());
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

    private Object toAvroEvent(OutboxEvent outboxEvent) throws Exception {
        return switch (outboxEvent.getEventType()) {
            case "InventoryReservedEvent" -> {
                InventoryReservedFields fields =
                        objectMapper.readValue(outboxEvent.getJsonPayload(), InventoryReservedFields.class);
                yield InventoryReservedEvent.newBuilder()
                        .setEventId(UUID.randomUUID().toString())
                        .setOrderId(fields.orderId())
                        .setReservedAt(Instant.now())
                        .build();
            }
            case "InventoryReservationFailedEvent" -> {
                InventoryReservationFailedFields fields =
                        objectMapper.readValue(outboxEvent.getJsonPayload(), InventoryReservationFailedFields.class);
                yield InventoryReservationFailedEvent.newBuilder()
                        .setEventId(UUID.randomUUID().toString())
                        .setOrderId(fields.orderId())
                        .setReason(fields.reason())
                        .setFailedAt(Instant.now())
                        .build();
            }
            default -> throw new IllegalStateException("Unknown outbox event type: " + outboxEvent.getEventType());
        };
    }
}
