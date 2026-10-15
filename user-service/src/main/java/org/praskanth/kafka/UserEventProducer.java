package org.praskanth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.events.PasswordResetEvent;
import org.praskanth.events.SendOtpEvent;
import org.praskanth.events.UserRegisteredEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventProducer {

    private static final String TOPIC = "user.registered";
    private static final String NOTIFICATION_TOPIC = "registration-otp";

    private static final String PASSWORD_RESET_TOPIC = "password-reset";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserRegistered(Long userId, String email, String name, String role) {
        UserRegisteredEvent event = UserRegisteredEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setUserId(userId)
                .setEmail(email)
                .setName(name)
                .setRole(role)
                .setRegisteredAt(Instant.now())
                .build();

        kafkaTemplate.send(TOPIC, String.valueOf(userId), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserRegisteredEvent for user {}", userId, ex);
                    } else {
                        log.info("Published UserRegisteredEvent eventId={} for user {} to partition {} offset {}",
                                event.getEventId(), userId,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    public void publishSendOtpEmail(String email, String otp) {
        SendOtpEvent emailEvent = SendOtpEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEmail(email)
                .setOtp(otp)
                .build();
        // Keyed by email to ensure OTPs for the same user process in order
        kafkaTemplate.send(NOTIFICATION_TOPIC, email, emailEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish SendOtpEmailEvent for email {}", email, ex);
                    } else {
                        log.info("Published SendOtpEmailEvent eventId={} for email {} to partition {} offset {}",
                                emailEvent.getEventId(), email,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    public void publishPasswordResetEvent(String email, String name, String token) {
        PasswordResetEvent passwordResetEvent = PasswordResetEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setEmail(email)
                .setName(name)
                .setResetToken(token)
                .setRequestedAt(Instant.now())
                .build();

        kafkaTemplate.send(PASSWORD_RESET_TOPIC, email, passwordResetEvent)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PasswordResetEvent for email {}", email, ex);
                    } else {
                        log.info("Published PasswordResetEvent eventId={} for email {} to partition {} offset {}",
                                passwordResetEvent.getEventId(), email,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
