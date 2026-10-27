package org.praskanth.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.events.ShipmentCreatedEvent;
import org.praskanth.service.OrderService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentCreatedConsumerTest {

    private static final String KEY = "processed-event:order-svc:shipment-created:evt-1";

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private OrderService orderService;
    @Mock
    private Acknowledgment ack;

    @InjectMocks
    private ShipmentCreatedConsumer consumer;

    private ShipmentCreatedEvent event;

    @BeforeEach
    void setUp() {
        event = new ShipmentCreatedEvent();
        event.setEventId("evt-1");
        event.setOrderId(42L);

    }

    private ConsumerRecord<String, ShipmentCreatedEvent> record() {
        return new ConsumerRecord<>("shipment.created", 0, 0L, "42", event);
    }

    @Test
    @DisplayName("first delivery: dedup key is set, the order is updated and the message acknowledged")
    void firstDelivery_processesAndAcks() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), eq("1"), any(Duration.class))).thenReturn(Boolean.TRUE);

        consumer.onShipmentCreated(record(), ack);

        verify(orderService).markShipped(42L);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("duplicate delivery: acknowledged without touching the order")
    void duplicateDelivery_acksWithoutProcessing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), eq("1"), any(Duration.class))).thenReturn(Boolean.FALSE);

        consumer.onShipmentCreated(record(), ack);

        verifyNoInteractions(orderService);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("Redis returning null is treated as a first delivery")
    void nullFromRedis_isTreatedAsFirstDelivery() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), eq("1"), any(Duration.class))).thenReturn((Boolean) null);

        consumer.onShipmentCreated(record(), ack);

        verify(orderService).markShipped(42L);
        verify(ack).acknowledge();
    }

    @Test
    @DisplayName("processing failure: dedup key is removed so the redelivery is retried, error is rethrown, no ack")
    void processingFails_removesDedupKeyAndRethrows() {
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq(KEY), eq("1"), any(Duration.class))).thenReturn(Boolean.TRUE);
        IllegalStateException boom = new IllegalStateException("boom");
        doThrow(boom).when(orderService).markShipped(42L);

        assertThatThrownBy(() -> consumer.onShipmentCreated(record(), ack)).isSameAs(boom);

        verify(redisTemplate).delete(KEY);
        verify(ack, never()).acknowledge();
    }
}
