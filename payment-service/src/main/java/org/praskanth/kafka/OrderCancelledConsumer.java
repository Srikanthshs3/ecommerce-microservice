package org.praskanth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.praskanth.events.OrderCancelledEvent;
import org.praskanth.service.PaymentSettlementService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** An order was cancelled: void its unpaid payment, or refund its share of a paid one. Same Redis-SETNX dedup as every consumer. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancelledConsumer {

    private static final String PROCESSED_KEY_PREFIX = "processed-event:payment-svc:order-cancelled:";
    private static final Duration DEDUP_WINDOW = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final PaymentSettlementService settlement;

    @KafkaListener(
            topics = "order.cancelled",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "orderCancelledContainerFactory"
    )
    public void onOrderCancelled(ConsumerRecord<String, OrderCancelledEvent> record, Acknowledgment ack) {
        OrderCancelledEvent event = record.value();
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();

        Boolean firstDelivery = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_WINDOW);
        if (Boolean.FALSE.equals(firstDelivery)) {
            ack.acknowledge();
            return;
        }
        try {
            settlement.onOrderCancelled(event.getOrderId(), String.valueOf(event.getReason()));
            ack.acknowledge();
        } catch (Exception ex) {
            redisTemplate.delete(dedupKey);
            log.error("Failed handling OrderCancelledEvent for order={} (eventId={})", event.getOrderId(), event.getEventId(), ex);
            throw ex;
        }
    }
}
