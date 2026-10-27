package org.praskanth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.events.PasswordResetEvent;
import org.praskanth.events.SendOtpEvent;
import org.praskanth.events.UserRegisteredEvent;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationConsumerTest {

    @Mock EmailService emailService;
    @Mock Acknowledgment ack;
    NotificationConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new NotificationConsumer(emailService);
        ReflectionTestUtils.setField(consumer, "frontendUrl", "http://localhost:5173");
    }

    @Test
    void otpEventSendsTheOtpEmailAndAcknowledges() {
        SendOtpEvent e = mock(SendOtpEvent.class);
        when(e.getEmail()).thenReturn("asha@example.com");
        when(e.getOtp()).thenReturn("123456");

        consumer.consumeVerificationEvent(e, ack);

        verify(emailService).sendOtpEmail("asha@example.com", "123456");
        verify(ack).acknowledge();
    }

    @Test
    void registrationEventSendsTheWelcomeEmail() {
        UserRegisteredEvent e = mock(UserRegisteredEvent.class);
        when(e.getEmail()).thenReturn("asha@example.com");

        consumer.consumerRegistrationEvent(e);

        verify(emailService).sendWelcomeEmail("asha@example.com");
    }

    @Test
    void passwordResetBuildsAClickableLinkWithAnEncodedToken() {
        PasswordResetEvent e = mock(PasswordResetEvent.class);
        when(e.getEmail()).thenReturn("asha@example.com");
        when(e.getResetToken()).thenReturn("a+b/c=");

        consumer.consumePasswordResetEvent(e, ack);

        ArgumentCaptor<String> link = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetEmail(org.mockito.ArgumentMatchers.eq("asha@example.com"), link.capture());
        assertThat(link.getValue()).isEqualTo("http://localhost:5173/reset-password?token=a%2Bb%2Fc%3D");
        verify(ack).acknowledge();
    }

    @Test
    void trailingSlashesOfTheFrontendUrlAreIgnored() {
        ReflectionTestUtils.setField(consumer, "frontendUrl", "https://shop.example.com//");
        PasswordResetEvent e = mock(PasswordResetEvent.class);
        when(e.getEmail()).thenReturn("a@b.com");
        when(e.getResetToken()).thenReturn("tok");

        consumer.consumePasswordResetEvent(e, ack);

        verify(emailService).sendPasswordResetEmail("a@b.com", "https://shop.example.com/reset-password?token=tok");
    }
}
