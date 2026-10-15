package org.praskanth.entity;

/**
 * Saga lifecycle:
 *   PENDING -(inventory reserved)-> AWAITING_PAYMENT -(payment ok)-> CONFIRMED -(shipped)-> SHIPPED
 *   PENDING -(insufficient stock)-> CANCELLED
 *   AWAITING_PAYMENT -(payment declined)-> CANCELLED  (inventory-service also releases its reservation)
 */
public enum OrderStatus {
    PENDING,
    AWAITING_PAYMENT,
    PLACED,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
