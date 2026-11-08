package org.praskanth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.praskanth.events.OrderCancelledEvent;
import org.praskanth.service.InventoryService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Compensation: an order was cancelled (payment window over, customer gave up ...), so the stock reserved for it goes
 * back to "available". releaseForOrder only touches reservations that are still RESERVED, so a repeated event, or one
 * for an order whose reservation never succeeded, changes nothing.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCancelledConsumer {

    private static final String PROCESSED_KEY_PREFIX = "processed-event:order-cancelled:";
    private static final Duration DEDUP_WINDOW = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final InventoryService inventoryService;

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
            log.info("Duplicate delivery of eventId={} for order={} - skipping", event.getEventId(), event.getOrderId());
            ack.acknowledge();
            return;
        }

        try {
            inventoryService.releaseForOrder(event.getOrderId());
            inventoryService.restockReturnedForOrder(event.getOrderId()); // only does something for parcels that already left
            ack.acknowledge();
        } catch (Exception ex) {
            redisTemplate.delete(dedupKey);
            log.error("Failed releasing reservation for cancelled order={} (eventId={}), releasing dedup claim for retry",
                    event.getOrderId(), event.getEventId(), ex);
            throw ex;
        }
    }
}
