package org.praskanth.entity;

public enum DiscountType {
    /** value = percent of the eligible amount (e.g. 10 = 10 %), optionally capped by maxDiscountAmount */
    PERCENTAGE,
    /** value = flat amount off (never more than the eligible amount) */
    FIXED
}
