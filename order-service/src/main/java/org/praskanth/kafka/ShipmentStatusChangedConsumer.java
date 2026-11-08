package org.praskanth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.praskanth.events.ShipmentStatusChangedEvent;
import org.praskanth.service.OrderService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Mirrors the shipment's progress onto the order (see OrderService.applyShipmentStatus). */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentStatusChangedConsumer {

    private static final String PROCESSED_KEY_PREFIX = "processed-event:order-svc:shipment-status:";
    private static final Duration DEDUP_WINDOW = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final OrderService orderService;

    @KafkaListener(
            topics = "shipment.status-changed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "shipmentStatusChangedContainerFactory"
    )
    public void onShipmentStatusChanged(ConsumerRecord<String, ShipmentStatusChangedEvent> record, Acknowledgment ack) {
        ShipmentStatusChangedEvent event = record.value();
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();

        Boolean firstDelivery = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_WINDOW);
        if (Boolean.FALSE.equals(firstDelivery)) {
            ack.acknowledge();
            return;
        }
        try {
            orderService.applyShipmentStatus(event.getOrderId(), event.getStatus().toString(), String.valueOf(event.getNote()));
            ack.acknowledge();
        } catch (Exception ex) {
            redisTemplate.delete(dedupKey);
            log.error("Failed handling ShipmentStatusChangedEvent for order={} (eventId={})", event.getOrderId(), event.getEventId(), ex);
            throw ex;
        }
    }
}
