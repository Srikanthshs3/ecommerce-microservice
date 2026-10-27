package org.praskanth.entity;

/**
 * CREATED            online payment started, waiting for the customer to pay
 * PENDING_COLLECTION cash on delivery: order placed, money is collected by the delivery agent
 * SUCCEEDED          paid online (verified with the gateway)
 * PARTIALLY_REFUNDED / REFUNDED   some / all of a paid checkout was given back (an order was cancelled)
 * CANCELLED          every order was cancelled before anything was paid (expired, or the customer gave up)
 */
public enum CheckoutPaymentStatus {
    CREATED,
    PENDING_COLLECTION,
    SUCCEEDED,
    PARTIALLY_REFUNDED,
    REFUNDED,
    CANCELLED,
    /** Cash on delivery: every parcel of the checkout was delivered and its cash collected. */
    COLLECTED
}
