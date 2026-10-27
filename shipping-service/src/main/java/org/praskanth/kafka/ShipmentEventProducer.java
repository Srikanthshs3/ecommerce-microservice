package org.praskanth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.events.ShipmentCreatedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentEventProducer {

    private static final String TOPIC = "shipment.created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishShipmentCreated(Long orderId, String trackingNumber, String carrier) {
        ShipmentCreatedEvent event = ShipmentCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(orderId)
                .setTrackingNumber(trackingNumber)
                .setCarrier(carrier)
                .setCreatedAt(Instant.now())
                .build();

        kafkaTemplate.send(TOPIC, String.valueOf(orderId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ShipmentCreatedEvent for order {}", orderId, ex);
                    } else {
                        log.info("Published ShipmentCreatedEvent eventId={} for order {}", event.getEventId(), orderId);
                    }
                });
    }
}
