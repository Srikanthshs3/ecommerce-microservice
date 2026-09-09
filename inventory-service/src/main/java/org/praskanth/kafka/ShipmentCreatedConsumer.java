package org.praskanth.kafka;

import org.praskanth.events.ShipmentCreatedEvent;
import org.praskanth.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Terminal Saga step for inventory: once shipped, the reservation is confirmed - permanently deducted, never returning to available stock. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentCreatedConsumer {

    private static final String PROCESSED_KEY_PREFIX = "processed-event:shipment-created:";
    private static final Duration DEDUP_WINDOW = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final InventoryService inventoryService;

    @KafkaListener(
            topics = "shipment.created",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "shipmentCreatedContainerFactory"
    )
    public void onShipmentCreated(ConsumerRecord<String, ShipmentCreatedEvent> record, Acknowledgment ack) {
        ShipmentCreatedEvent event = record.value();
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();

        Boolean firstDelivery = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_WINDOW);
        if (Boolean.FALSE.equals(firstDelivery)) {
            log.info("Duplicate delivery of eventId={} for order={} - skipping", event.getEventId(), event.getOrderId());
            ack.acknowledge();
            return;
        }

        try {
            inventoryService.confirmForOrder(event.getOrderId());
            ack.acknowledge();
        } catch (Exception ex) {
            redisTemplate.delete(dedupKey);
            log.error("Failed confirming reservation for order={} (eventId={}), releasing dedup claim for retry",
                    event.getOrderId(), event.getEventId(), ex);
            throw ex;
        }
    }
}
