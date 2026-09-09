package org.praskanth.kafka;

import org.praskanth.entity.Order;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.events.OrderCreatedEvent;
import org.praskanth.events.OrderItemPayload;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventProducer {

    private static final String TOPIC = "order.created";

    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;

    public void publishOrderCreated(Order order) {
        List<OrderItemPayload> items = order.getItems().stream()
                .map(item -> OrderItemPayload.newBuilder()
                        .setProductId(item.getProductId())
                        .setQuantity(item.getQuantity())
                        .setUnitPrice(toByteBuffer(item.getUnitPrice()))
                        .build())
                .toList();

        OrderCreatedEvent event = OrderCreatedEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setOrderId(order.getId())
                .setUserId(order.getUserId())
                .setItems(items)
                .setTotalAmount(toByteBuffer(order.getTotalAmount()))
                .setCreatedAt(order.getCreatedAt())
                .build();

        kafkaTemplate.send(TOPIC, String.valueOf(order.getId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish OrderCreatedEvent for order {}", order.getId(), ex);
                    } else {
                        log.info("Published OrderCreatedEvent eventId={} for order {}", event.getEventId(), order.getId());
                    }
                });
    }

    private ByteBuffer toByteBuffer(BigDecimal value) {
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return ByteBuffer.wrap(scaled.unscaledValue().toByteArray());
    }
}
