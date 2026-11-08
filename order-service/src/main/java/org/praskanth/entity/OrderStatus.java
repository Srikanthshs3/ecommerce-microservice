package org.praskanth.entity;

/**
 * Lifecycle (the order is the "scoreboard"; shipping-service owns the shipment and tells us where it is):
 *
 *   PENDING            stock is being reserved
 *   AWAITING_PAYMENT   stock reserved, the customer has until expiresAt to pay (or choose cash on delivery)
 *   CONFIRMED          paid / cash on delivery chosen: "order placed", the seller must pack it
 *   READY_TO_SHIP      the seller packed it, a delivery agent can pick it up
 *   OUT_FOR_DELIVERY   a delivery agent has it
 *   DELIVERED          handed over (delivery code confirmed)
 *   CANCELLED          from PENDING .. READY_TO_SHIP only (payment window over, customer or seller cancelled)
 *
 * PLACED and SHIPPED are legacy values kept so old rows still load.
 */
public enum OrderStatus {
    PENDING,
    AWAITING_PAYMENT,
    PLACED,
    CONFIRMED,
    READY_TO_SHIP,
    SHIPPED,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED
}
