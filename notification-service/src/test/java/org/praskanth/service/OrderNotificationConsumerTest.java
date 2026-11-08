package org.praskanth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.events.OrderNotificationEvent;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.mail.MailSendException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class OrderNotificationConsumerTest {

    @Mock EmailService emailService;
    @Mock Acknowledgment ack;
    OrderNotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OrderNotificationConsumer(emailService);
        ReflectionTestUtils.setField(consumer, "frontendUrl", "https://shop.example.com/");
    }

    private OrderNotificationEvent event(String stage, String email) {
        OrderNotificationEvent e = new OrderNotificationEvent();
        e.setEventId("e-1");
        e.setOrderId(42L);
        e.setUserId(5L);
        e.setStage(stage);
        e.setCustomerEmail(email);
        e.setTotalAmount("800.00");
        e.setItems(List.of());
        e.setOccurredAt(java.time.Instant.now());
        return e;
    }

    @Test
    void sendsTheEmailAndAcknowledges() {
        consumer.onOrderNotification(event("PACKED", "asha@example.com"), ack);

        verify(emailService).sendHtmlEmail(eq("asha@example.com"), contains("#42"), contains("https://shop.example.com/orders"), anyString());
        verify(ack).acknowledge();
    }

    @Test
    void noEmailAddress_isSkippedAndAcknowledged() {
        consumer.onOrderNotification(event("PACKED", null), ack);

        verifyNoInteractions(emailService);
        verify(ack).acknowledge();
    }

    @Test
    void unknownStage_isSkippedAndAcknowledged() {
        consumer.onOrderNotification(event("MYSTERY", "asha@example.com"), ack);

        verifyNoInteractions(emailService);
        verify(ack).acknowledge();
    }

    @Test
    void mailServerFailure_isNotAcknowledgedSoItIsRetried() {
        doThrow(new MailSendException("smtp down")).when(emailService).sendHtmlEmail(anyString(), anyString(), anyString(), anyString());

        assertThatThrownBy(() -> consumer.onOrderNotification(event("DELIVERED", "asha@example.com"), ack))
                .isInstanceOf(MailSendException.class);
        verify(ack, never()).acknowledge();
    }
}
