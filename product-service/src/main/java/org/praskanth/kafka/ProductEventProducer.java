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

    /** Avro's decimal logical type is encoded as scaled unscaled-bytes, per the .avsc precision/scale. */
    private ByteBuffer toByteBuffer(BigDecimal value) {
        BigDecimal scaled = value.setScale(2, java.math.RoundingMode.HALF_UP);
        return ByteBuffer.wrap(scaled.unscaledValue().toByteArray());
    }
}
