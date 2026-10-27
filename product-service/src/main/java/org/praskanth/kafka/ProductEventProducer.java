package org.praskanth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.events.ProductCreatedEvent;
import org.praskanth.model.Product;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProductEventProducer {

    private static final String TOPIC = "product.created";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishProductCreated(Product product) {
        ProductCreatedEvent event = ProductCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setProductId(product.getId())
                .setName(product.getName())
                .setSellerId(product.getSellerId())
                .setCategoryId(product.getCategoryId())
                .setPrice(toByteBuffer(product.getMrpPrice()))
                .setInitialStock(product.getStock() == null ? 0 : Math.max(0, product.getStock()))
                .setCreatedAt(product.getCreatedAt() != null ? product.getCreatedAt() : Instant.now())
                .build();

        kafkaTemplate.send(TOPIC, product.getId(), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ProductCreatedEvent for product {}", product.getId(), ex);
                    } else {
                        log.info("Published ProductCreatedEvent eventId={} for product {}",
                                event.getEventId(), product.getId());
                    }
                });
    }

    /**
     * One inventory row per variant. The variant's stock key (productId:variantId) travels in the event's productId
     * field, so inventory-service needs no change: it simply provisions a row for that key with the opening stock.
     */
    public void publishVariantCreated(Product product, String stockId, String label, BigDecimal price, int initialStock) {
        ProductCreatedEvent event = ProductCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setProductId(stockId)
                .setName(product.getName() + " (" + label + ")")
                .setSellerId(product.getSellerId())
                .setCategoryId(product.getCategoryId())
                .setPrice(toByteBuffer(price))
                .setInitialStock(Math.max(0, initialStock))
                .setCreatedAt(product.getCreatedAt() != null ? product.getCreatedAt() : Instant.now())
                .build();

        kafkaTemplate.send(TOPIC, stockId, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish ProductCreatedEvent for variant {}", stockId, ex);
                    } else {
                        log.info("Published ProductCreatedEvent eventId={} for variant {}", event.getEventId(), stockId);
                    }
                });
    }

    /** Avro's decimal logical type is encoded as scaled unscaled-bytes, per the .avsc precision/scale. */
    private ByteBuffer toByteBuffer(BigDecimal value) {
        BigDecimal scaled = value.setScale(2, java.math.RoundingMode.HALF_UP);
        return ByteBuffer.wrap(scaled.unscaledValue().toByteArray());
    }
}
