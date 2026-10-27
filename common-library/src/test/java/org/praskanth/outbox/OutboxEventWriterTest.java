package org.praskanth.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OutboxEventWriterTest {

    @Mock
    private OutboxEventRepository repository;

    public static class Unserializable {
        public String getBroken() {
            throw new IllegalStateException("cannot read");
        }
    }

    @Test
    @DisplayName("write saves one PENDING row with the serialized payload")
    void write_savesPendingEvent() {
        // Given
        OutboxEventWriter writer = new OutboxEventWriter(repository, new ObjectMapper());
        // When
        writer.write("ORDER_CREATED", "order-events", "42", Map.of("orderId", 42));
        // Then
        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());
        OutboxEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("ORDER_CREATED");
        assertThat(saved.getTopic()).isEqualTo("order-events");
        assertThat(saved.getPartitionKey()).isEqualTo("42");
        assertThat(saved.getJsonPayload()).isEqualTo("{\"orderId\":42}");
        assertThat(saved.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);
        assertThat(saved.getAttemptCount()).isZero();
    }

    @Test
    @DisplayName("write propagates serialization failures and saves nothing")
    void write_unserializablePayload_throwsAndDoesNotSave() {
        OutboxEventWriter writer = new OutboxEventWriter(repository, new ObjectMapper());

        assertThatThrownBy(() -> writer.write("T", "topic", "k", new Unserializable()))
                .isInstanceOf(JsonProcessingException.class);

        verify(repository, never()).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("onCreate fills createdAt and the default status")
    void onCreate_setsDefaults() {
        OutboxEvent event = new OutboxEvent();

        event.onCreate();

        assertThat(event.getCreatedAt()).isNotNull();
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("onCreate keeps explicit createdAt and status")
    void onCreate_keepsExistingValues() {
        java.time.Instant created = java.time.Instant.parse("2024-01-01T00:00:00Z");
        OutboxEvent event = OutboxEvent.builder().createdAt(created).status(OutboxEvent.OutboxStatus.FAILED).build();

        event.onCreate();

        assertThat(event.getCreatedAt()).isEqualTo(created);
        assertThat(event.getStatus()).isEqualTo(OutboxEvent.OutboxStatus.FAILED);
    }
}
