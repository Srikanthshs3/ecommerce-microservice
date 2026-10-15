package org.praskanth.kafka;

import org.praskanth.events.PaymentFailedEvent;
import org.praskanth.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Compensating action for the Saga: a failed charge means the stock we reserved for this order must go back to available. */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentFailedConsumer {

    private static final String PROCESSED_KEY_PREFIX = "processed-event:payment-failed:";
    private static final Duration DEDUP_WINDOW = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final InventoryService inventoryService;

    @KafkaListener(
            topics = "payment.failed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "paymentFailedContainerFactory"
    )
    public void onPaymentFailed(ConsumerRecord<String, PaymentFailedEvent> record, Acknowledgment ack) {
        PaymentFailedEvent event = record.value();
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();

        Boolean firstDelivery = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_WINDOW);
        if (Boolean.FALSE.equals(firstDelivery)) {
            log.info("Duplicate delivery of eventId={} for order={} - skipping", event.getEventId(), event.getOrderId());
            ack.acknowledge();
            return;
        }

        try {
            inventoryService.releaseForOrder(event.getOrderId());
            ack.acknowledge();
        } catch (Exception ex) {
            redisTemplate.delete(dedupKey);
            log.error("Failed releasing reservation for order={} (eventId={}), releasing dedup claim for retry",
                    event.getOrderId(), event.getEventId(), ex);
            throw ex;
        }
    }
}
