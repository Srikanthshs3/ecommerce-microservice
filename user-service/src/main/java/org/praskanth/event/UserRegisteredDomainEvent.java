package org.praskanth.event;

/**
 * In-process domain event. Published inside the registration transaction and forwarded to Kafka
 * only AFTER the transaction commits, so other services never hear about a user that was rolled back.
 */
public record UserRegisteredDomainEvent(Long userId, String email, String name, String role) {}
