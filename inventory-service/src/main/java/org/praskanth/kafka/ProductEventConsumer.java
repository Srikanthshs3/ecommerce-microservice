package org.praskanth.kafka;

import org.praskanth.events.ProductCreatedEvent;
import org.praskanth.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Same Redis-SETNX idempotency pattern as product-service's UserEventConsumer - see that class for the full rationale. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventConsumer {

    private static final String PROCESSED_KEY_PREFIX = "processed-event:product-created:";
    private static final Duration DEDUP_WINDOW = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final InventoryService inventoryService;

    @KafkaListener(
            topics = "product.created",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "productCreatedContainerFactory"
    )
    public void onProductCreated(ConsumerRecord<String, ProductCreatedEvent> record, Acknowledgment ack) {
        ProductCreatedEvent event = record.value();
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();

        Boolean firstDelivery = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_WINDOW);
        if (Boolean.FALSE.equals(firstDelivery)) {
            log.info("Duplicate delivery of eventId={} for product={} - skipping", event.getEventId(), event.getProductId());
            ack.acknowledge();
            return;
        }

        try {
            String productId = event.getProductId().toString();
            String productName = event.getName() != null ? event.getName().toString() : "Unknown";
            Long sellerId = event.getSellerId();
            inventoryService.provisionIfAbsent(productId, productName, sellerId, event.getInitialStock());
            ack.acknowledge();
        } catch (Exception ex) {
            redisTemplate.delete(dedupKey);
            log.error("Failed processing eventId={}, releasing dedup claim for retry", event.getEventId(), ex);
            throw ex;
        }
    }
}
