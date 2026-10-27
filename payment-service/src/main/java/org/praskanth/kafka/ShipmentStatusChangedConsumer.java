package org.praskanth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.praskanth.events.ShipmentStatusChangedEvent;
import org.praskanth.service.PaymentSettlementService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** A parcel was delivered: for cash on delivery that is the moment the money counts as collected. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentStatusChangedConsumer {

    private static final String PROCESSED_KEY_PREFIX = "processed-event:payment-svc:shipment-status:";
    private static final Duration DEDUP_WINDOW = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final PaymentSettlementService settlement;

    @KafkaListener(
            topics = "shipment.status-changed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "shipmentStatusChangedContainerFactory"
    )
    public void onShipmentStatusChanged(ConsumerRecord<String, ShipmentStatusChangedEvent> record, Acknowledgment ack) {
        ShipmentStatusChangedEvent event = record.value();
        if (!"DELIVERED".equals(event.getStatus().toString())) {
            ack.acknowledge();
            return;
        }
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();
        Boolean first = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_WINDOW);
        if (Boolean.FALSE.equals(first)) {
            ack.acknowledge();
            return;
        }
        try {
            settlement.onDelivered(event.getOrderId());
            ack.acknowledge();
        } catch (Exception ex) {
            redisTemplate.delete(dedupKey);
            log.error("Failed recording delivery for order={} (eventId={})", event.getOrderId(), event.getEventId(), ex);
            throw ex;
        }
    }
}
