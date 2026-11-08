package org.praskanth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.praskanth.events.ShipmentStatusChangedEvent;
import org.praskanth.service.InventoryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Set;

/**
 * Stock stays "reserved" while the parcel is still with the seller (so a cancel puts it straight back). Once a delivery
 * agent has it (OUT_FOR_DELIVERY) the sale is final: reserved stock is permanently deducted. confirmForOrder only touches
 * reservations that are still RESERVED, so duplicates and later statuses (DELIVERED) are no-ops.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentStatusChangedConsumer {

    private static final String PROCESSED_KEY_PREFIX = "processed-event:inventory-svc:shipment-status:";
    private static final Duration DEDUP_WINDOW = Duration.ofDays(7);
    private static final Set<String> FINALISING = Set.of("OUT_FOR_DELIVERY", "DELIVERED");

    private final StringRedisTemplate redisTemplate;
    private final InventoryService inventoryService;

    @KafkaListener(
            topics = "shipment.status-changed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "shipmentStatusChangedContainerFactory"
    )
    public void onShipmentStatusChanged(ConsumerRecord<String, ShipmentStatusChangedEvent> record, Acknowledgment ack) {
        ShipmentStatusChangedEvent event = record.value();
        if (!FINALISING.contains(event.getStatus().toString())) {
            ack.acknowledge();
            return;
        }
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();
        Boolean firstDelivery = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_WINDOW);
        if (Boolean.FALSE.equals(firstDelivery)) {
            ack.acknowledge();
            return;
        }
        try {
            inventoryService.confirmForOrder(event.getOrderId());
            ack.acknowledge();
        } catch (Exception ex) {
            redisTemplate.delete(dedupKey);
            log.error("Failed finalising stock for order={} (eventId={})", event.getOrderId(), event.getEventId(), ex);
            throw ex;
        }
    }
}
