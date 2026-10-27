package org.praskanth.notification;

/** The customer-facing steps of an order that trigger an e-mail. The name travels in the event as plain text. */
public enum OrderStage {
    /** Paid (or cash on delivery chosen): the order is confirmed with the seller. */
    ORDER_PLACED,
    /** The seller packed it. */
    PACKED,
    /** A delivery partner picked it up. */
    OUT_FOR_DELIVERY,
    DELIVERED,
    /** Cancelled by the customer, the seller, an unpaid timeout or a failed delivery. */
    CANCELLED,
    /** Cancelled because an item could not be reserved. */
    OUT_OF_STOCK
}
