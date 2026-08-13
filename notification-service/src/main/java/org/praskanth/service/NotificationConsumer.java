package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.events.PasswordResetEvent;
import org.praskanth.events.SendOtpEvent;
import org.praskanth.events.UserRegisteredEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationConsumer {
    private final EmailService emailService;


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
        String resetLink = event.getResetToken().toString();
        emailService.sendPasswordResetEmail(email, resetLink);
        ack.acknowledge();
    }
}
