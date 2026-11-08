package org.praskanth.kafka;

import org.praskanth.events.PaymentCompletedEvent;
import org.praskanth.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentCompletedConsumer {

    private static final String PROCESSED_KEY_PREFIX = "processed-event:order-svc:payment-completed:";
    private static final Duration DEDUP_WINDOW = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final OrderService orderService;

    @KafkaListener(
            topics = "payment.completed",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "paymentCompletedContainerFactory"
    )
    public void onPaymentCompleted(ConsumerRecord<String, PaymentCompletedEvent> record, Acknowledgment ack) {
        PaymentCompletedEvent event = record.value();
        String dedupKey = PROCESSED_KEY_PREFIX + event.getEventId();

        Boolean firstDelivery = redisTemplate.opsForValue().setIfAbsent(dedupKey, "1", DEDUP_WINDOW);
        if (Boolean.FALSE.equals(firstDelivery)) {
            ack.acknowledge();
            return;
        }

        try {
            orderService.markConfirmed(event.getOrderId(), event.getMethod() == null ? null : event.getMethod().toString());
            ack.acknowledge();
        } catch (Exception ex) {
            redisTemplate.delete(dedupKey);
            log.error("Failed handling PaymentCompletedEvent for order={} (eventId={})", event.getOrderId(), event.getEventId(), ex);
            throw ex;
        }
    }
}
