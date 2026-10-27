package org.praskanth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock JavaMailSender mailSender;
    EmailService service;

    @BeforeEach
    void setUp() {
        service = new EmailService(mailSender);
        ReflectionTestUtils.setField(service, "username", "noreply@eshop.test");
    }

    private SimpleMailMessage sent() {
        ArgumentCaptor<SimpleMailMessage> c = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(c.capture());
        return c.getValue();
    }

    @Test
    void otpEmailContainsTheCodeAndExpiry() {
        service.sendOtpEmail("asha@example.com", "482913");
        SimpleMailMessage m = sent();
        assertThat(m.getTo()).containsExactly("asha@example.com");
        assertThat(m.getFrom()).isEqualTo("noreply@eshop.test");
        assertThat(m.getSubject()).contains("OTP");
        assertThat(m.getText()).contains("482913").contains("5 minutes");
    }

    @Test
    void welcomeEmailIsAddressedToTheNewUser() {
        service.sendWelcomeEmail("asha@example.com");
        SimpleMailMessage m = sent();
        assertThat(m.getTo()).containsExactly("asha@example.com");
        assertThat(m.getSubject()).containsIgnoringCase("welcome");
    }

    @Test
    void passwordResetEmailContainsTheLinkAndTheValidityNote() {
        service.sendPasswordResetEmail("asha@example.com", "http://localhost:5173/reset-password?token=abc");
        SimpleMailMessage m = sent();
        assertThat(m.getText()).contains("http://localhost:5173/reset-password?token=abc").contains("15 minutes");
        assertThat(m.getSubject()).containsIgnoringCase("password reset");
    }

    @Test
    void aMailServerFailureNeverEscapesTheSender() {
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));
        assertThatCode(() -> service.sendOtpEmail("a@b.com", "1")).doesNotThrowAnyException();
        assertThatCode(() -> service.sendWelcomeEmail("a@b.com")).doesNotThrowAnyException();
        assertThatCode(() -> service.sendPasswordResetEmail("a@b.com", "l")).doesNotThrowAnyException();
    }

    @Test
    void htmlEmail_isBuiltAsMultipartAndSent() throws Exception {
        MimeMessage mime = new MimeMessage((Session) null);
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenReturn(mime);

        service.sendHtmlEmail("asha@example.com", "Order #42 is packed", "<p>hi</p>", "hi");

        verify(mailSender).send(mime);
        assertThat(mime.getSubject()).isEqualTo("Order #42 is packed");
        assertThat(mime.getAllRecipients()[0].toString()).isEqualTo("asha@example.com");
    }

    @Test
    void htmlEmail_sendFailureIsRethrownSoTheKafkaEventIsRetried() {
        MimeMessage mime = new MimeMessage((Session) null);
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenReturn(mime);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(MimeMessage.class));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sendHtmlEmail("asha@example.com", "s", "<p>x</p>", "x"))
                .isInstanceOf(MailSendException.class);
    }
}
