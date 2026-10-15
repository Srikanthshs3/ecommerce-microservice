package org.praskanth.entity;

public enum RedemptionStatus {
    /** The coupon is used up by a checkout (counts against usage limits). */
    RESERVED,
    /** The checkout failed or all its orders were cancelled: the use is given back. */
    RELEASED
}
