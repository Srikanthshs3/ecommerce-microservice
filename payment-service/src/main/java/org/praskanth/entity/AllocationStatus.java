package org.praskanth.entity;

public enum AllocationStatus {
    ACTIVE,
    /** Cash on delivery: the agent handed the cash in and the customer got the parcel. */
    COLLECTED,
    CANCELLED,
    REFUNDED
}
