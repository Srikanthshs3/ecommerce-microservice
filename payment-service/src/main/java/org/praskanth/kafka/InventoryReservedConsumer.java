package org.praskanth.kafka;

import org.praskanth.events.InventoryReservedEvent;
import org.praskanth.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Same Redis-SETNX idempotency pattern used by every consumer in this platform. */
@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryReservedConsumer {

    private static final String PROCESSED_KEY_PREFIX = "processed-event:inventory-reserved:";
    private static final Duration DEDUP_WINDOW = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final PaymentService paymentService;

    @KafkaListener(
            topics = "inventory.reserved",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryReserved(ConsumerRecord<String, InventoryReservedEvent> record, Acknowledgment ack) {
        InventoryReservedEvent event = record.value();
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();

        Boolean firstDelivery = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_WINDOW);
        if (Boolean.FALSE.equals(firstDelivery)) {
            log.info("Duplicate delivery of eventId={} for order={} - skipping", event.getEventId(), event.getOrderId());
            ack.acknowledge();
            return;
        }

        try {
            paymentService.processPaymentForOrder(event.getOrderId());
            ack.acknowledge();
        } catch (Exception ex) {
            redisTemplate.delete(dedupKey);
            log.error("Failed processing eventId={} for order={}, releasing dedup claim for retry",
                    event.getEventId(), event.getOrderId(), ex);
            throw ex;
        }
    }
}
