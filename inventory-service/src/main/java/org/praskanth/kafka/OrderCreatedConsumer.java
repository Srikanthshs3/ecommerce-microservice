package org.praskanth.kafka;

import org.praskanth.dto.ReservationItem;
import org.praskanth.events.OrderCreatedEvent;
import org.praskanth.events.OrderItemPayload;
import org.praskanth.exception.ConflictException;
import org.praskanth.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * First Saga step on the inventory side. On success, publishes InventoryReservedEvent; on
 * failure (insufficient stock on any line item - InventoryService.reserveForOrder already
 * rolled back whatever it managed to reserve), publishes InventoryReservationFailedEvent instead.
 * Same Redis-SETNX idempotency pattern used by every consumer in this platform.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCreatedConsumer {

    private static final String PROCESSED_KEY_PREFIX = "processed-event:order-created:";
    private static final Duration DEDUP_WINDOW = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final InventoryService inventoryService;
    private final InventoryEventProducer inventoryEventProducer;

    @KafkaListener(
            topics = "order.created",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "orderCreatedContainerFactory"
    )
    public void onOrderCreated(ConsumerRecord<String, OrderCreatedEvent> record, Acknowledgment ack) {
        OrderCreatedEvent event = record.value();
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();

        Boolean firstDelivery = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_WINDOW);
        if (Boolean.FALSE.equals(firstDelivery)) {
            log.info("Duplicate delivery of eventId={} for order={} - skipping", event.getEventId(), event.getOrderId());
            ack.acknowledge();
            return;
        }

        try {
            List<ReservationItem> items = event.getItems().stream()
                    .map(this::toReservationItem)
                    .toList();

            try {
                inventoryService.reserveForOrder(event.getOrderId(), items);
                inventoryEventProducer.publishInventoryReserved(event.getOrderId());
            } catch (ConflictException ex) {
                inventoryEventProducer.publishInventoryReservationFailed(event.getOrderId(), ex.getMessage());
            }

            ack.acknowledge();
        } catch (Exception ex) {
            // Only unexpected errors (not ConflictException, which is a legitimate business
            // outcome already handled above) release the dedup claim for a retry.
            redisTemplate.delete(dedupKey);
            log.error("Failed processing eventId={} for order={}, releasing dedup claim for retry",
                    event.getEventId(), event.getOrderId(), ex);
            throw ex;
        }
    }

    private ReservationItem toReservationItem(OrderItemPayload payload) {
        return new ReservationItem(payload.getProductId(), payload.getQuantity());
    }
}
