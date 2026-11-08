package org.praskanth.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.events.UserRegisteredEvent;
import org.praskanth.service.EmailService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.kafka.support.Acknowledgment;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserEventConsumerTest {

    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> ops;
    @Mock EmailService emailService;
    @Mock Acknowledgment ack;
    UserEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new UserEventConsumer(redis, emailService);
        when(redis.opsForValue()).thenReturn(ops);
    }

    private UserRegisteredEvent event() {
        UserRegisteredEvent e = mock(UserRegisteredEvent.class);
        when(e.getEventId()).thenReturn("ev-1");
        return e;
    }

    private static ConsumerRecord<String, UserRegisteredEvent> rec(UserRegisteredEvent e) {
        return new ConsumerRecord<>("user.registered", 0, 0L, "k", e);
    }

    @Test
    void firstDeliverySendsWelcomeAndAcks() {
        UserRegisteredEvent e = event();
        when(e.getEmail()).thenReturn("asha@example.com");
        when(ops.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);

        consumer.onUserRegistered(rec(e), ack);

        verify(emailService).sendWelcomeEmail("asha@example.com");
        verify(ack).acknowledge();
    }

    @Test
    void duplicateDeliveryIsAckedWithoutSendingAnotherEmail() {
        UserRegisteredEvent e = event();
        when(ops.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        consumer.onUserRegistered(rec(e), ack);

        verifyNoInteractions(emailService);
        verify(ack).acknowledge();
    }

    @Test
    void failureReleasesTheClaimSoTheRetryIsProcessed() {
        UserRegisteredEvent e = event();
        when(e.getEmail()).thenReturn("asha@example.com");
        when(ops.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        doThrow(new IllegalStateException("smtp")).when(emailService).sendWelcomeEmail("asha@example.com");

        assertThatThrownBy(() -> consumer.onUserRegistered(rec(e), ack)).isInstanceOf(IllegalStateException.class);

        verify(redis).delete(anyString());
        verify(ack, never()).acknowledge();
    }
}
