package org.praskanth.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.event.UserRegisteredDomainEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Bridges in-process domain events to Kafka after the DB transaction commits.
 * NOTE: this removes "event for a rolled-back user", but a crash between commit and send can still lose
 * the event. For guaranteed delivery replace with a transactional outbox (see REFACTORING_NOTES.md).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventListener {

    private final UserEventProducer producer;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserRegistered(UserRegisteredDomainEvent event) {
        try {
            producer.publishUserRegistered(event.userId(), event.email(), event.name(), event.role());
        } catch (RuntimeException ex) {
            // The user is already committed; never fail the request because the broker is unavailable.
            log.error("Could not publish UserRegistered for user {}", event.userId(), ex);
        }
    }
}
