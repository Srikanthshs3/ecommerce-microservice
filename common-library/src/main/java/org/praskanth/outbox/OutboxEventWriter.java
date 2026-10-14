package org.praskanth.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

/**
 * Call this INSIDE the same @Transactional method that saves the business entity. It does
 * nothing more than a normal JpaRepository.save() - no Kafka call, no network I/O - so it
 * participates in whatever transaction is already open and commits/rolls back with it.
 * The actual Kafka publish happens later, out-of-band, in the service's OutboxPoller.
 */
@ConditionalOnClass(name = "org.springframework.data.jpa.repository.JpaRepository")
@Component
@RequiredArgsConstructor
public class OutboxEventWriter {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @SneakyThrows
    public void write(String eventType, String topic, String partitionKey, Object fields) {
        OutboxEvent event = OutboxEvent.builder()
                .eventType(eventType)
                .topic(topic)
                .partitionKey(partitionKey)
                .jsonPayload(objectMapper.writeValueAsString(fields))
                .status(OutboxEvent.OutboxStatus.PENDING)
                .attemptCount(0)
                .build();
        outboxEventRepository.save(event);
    }
}
