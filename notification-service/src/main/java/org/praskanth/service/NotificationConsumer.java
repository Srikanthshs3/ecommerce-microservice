package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.events.PasswordResetEvent;
import org.praskanth.events.SendOtpEvent;
import org.praskanth.events.UserRegisteredEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {
    private final EmailService emailService;

    /** Address of the web app, used to build the link in the password-reset email (APP_FRONTEND_URL). */
    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;


    @KafkaListener(topics = "registration-otp",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumeVerificationEvent(SendOtpEvent event, Acknowledgment ack) {
        System.out.println("Received verification event for email: " + event.getEmail() + " with OTP: " + event.getOtp());
        log.info("Received verification event for email: {}", event.getEmail());
        String email = event.getEmail().toString();
        String otp = event.getOtp().toString();
        // Simulate sending an email
        emailService.sendOtpEmail(email, otp);
        ack.acknowledge();

    }

    @KafkaListener(topics = "user.registered",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumerRegistrationEvent(UserRegisteredEvent event) {
        log.info("Received registration event for email: {}", event.getEmail());
        String email = event.getEmail().toString();
        emailService.sendWelcomeEmail(email);
    }

    @KafkaListener(topics = "password-reset",
            groupId = "notification-group",
            containerFactory = "kafkaListenerContainerFactory")
    public void consumePasswordResetEvent(PasswordResetEvent event, Acknowledgment ack) {
        log.info("Received password reset event for email: {}", event.getEmail());
        String email = event.getEmail().toString();
        // The email must contain a clickable link to the reset page, not just the bare token.
        String token = event.getResetToken().toString();
        String resetLink = frontendUrl.replaceAll("/+$", "") + "/reset-password?token="
                + URLEncoder.encode(token, StandardCharsets.UTF_8);
        emailService.sendPasswordResetEmail(email, resetLink);
        ack.acknowledge();
    }
}
