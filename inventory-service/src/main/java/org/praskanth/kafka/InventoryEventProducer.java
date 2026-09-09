package org.praskanth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.events.InventoryReservationFailedEvent;
import org.praskanth.events.InventoryReservedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventProducer {

    private static final String RESERVED_TOPIC = "inventory.reserved";
    private static final String FAILED_TOPIC = "inventory.reservation-failed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishInventoryReserved(Long orderId) {
        InventoryReservedEvent event = InventoryReservedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(orderId)
                .setReservedAt(Instant.now())
                .build();

        kafkaTemplate.send(RESERVED_TOPIC, String.valueOf(orderId), event)
                .whenComplete((result, ex) -> logResult(ex, "InventoryReservedEvent", orderId, event.getEventId()));
    }

    public void publishInventoryReservationFailed(Long orderId, String reason) {
        InventoryReservationFailedEvent event = InventoryReservationFailedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(orderId)
                .setReason(reason)
                .setFailedAt(Instant.now())
                .build();

        kafkaTemplate.send(FAILED_TOPIC, String.valueOf(orderId), event)
                .whenComplete((result, ex) -> logResult(ex, "InventoryReservationFailedEvent", orderId, event.getEventId()));
    }

    private void logResult(Throwable ex, String eventType, Long orderId, String eventId) {
        if (ex != null) {
            log.error("Failed to publish {} for order {}", eventType, orderId, ex);
        } else {
            log.info("Published {} eventId={} for order {}", eventType, eventId, orderId);
        }
    }
}
