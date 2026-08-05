package org.praskanth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.entity.User;
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

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void publishUserRegistered(User user) {
        UserRegisteredEvent event = UserRegisteredEvent.newBuilder()
                .setEventId(UUID.randomUUID().toString())
                .setUserId(user.getId())
                .setEmail(user.getEmail())
                .setName(user.getName())
                .setRegisteredAt(Instant.now())
                .build();

        kafkaTemplate.send(TOPIC, String.valueOf(user.getId()), event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish UserRegisteredEvent for user {}", user.getId(), ex);
                    } else {
                        log.info("Published UserRegisteredEvent eventId={} for user {} to partition {} offset {}",
                                event.getEventId(), user.getId(),
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
        System.out.println("Publishing SendOtpEmailEvent for email " + email + " with OTP " + otp);
        // Keyed by email to ensure OTPs for the same user process in order
        kafkaTemplate.send(NOTIFICATION_TOPIC, emailEvent)
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
}
