package org.praskanth.kafka;

import org.praskanth.events.PaymentCompletedEvent;
import org.praskanth.service.ShippingService;
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
public class PaymentCompletedConsumer {

    private static final String PROCESSED_KEY_PREFIX = "processed-event:payment-completed:";
    private static final Duration DEDUP_WINDOW = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ShippingService shippingService;

    @KafkaListener(
            topics = "payment.completed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentCompleted(ConsumerRecord<String, PaymentCompletedEvent> record, Acknowledgment ack) {
        PaymentCompletedEvent event = record.value();
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();

        Boolean firstDelivery = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_WINDOW);
        if (Boolean.FALSE.equals(firstDelivery)) {
            log.info("Duplicate delivery of eventId={} for order={} - skipping", event.getEventId(), event.getOrderId());
            ack.acknowledge();
            return;
        }

        try {
            shippingService.createShipmentForOrder(event.getOrderId());
            ack.acknowledge();
        } catch (Exception ex) {
            redisTemplate.delete(dedupKey);
            log.error("Failed processing eventId={} for order={}, releasing dedup claim for retry",
                    event.getEventId(), event.getOrderId(), ex);
            throw ex;
        }
    }
}
