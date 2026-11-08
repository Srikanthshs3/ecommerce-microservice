package org.praskanth.entity;

import java.util.Map;
import java.util.Set;

/**
 * The shipment state machine. Every change goes through {@link #canMoveTo}: an illegal jump (for example a seller
 * cancelling a parcel that is already out for delivery) is refused, whoever asks and however the request arrives.
 *
 *   AWAITING_SELLER --seller packs--> READY_TO_SHIP --agent picks up--> OUT_FOR_DELIVERY --> DELIVERED
 *        |                                |                                   |
 *        +----------- CANCELLED <---------+                                   +--> DELIVERY_FAILED
 *
 * CREATED and IN_TRANSIT are the old simulated values; rows that still have them are left alone.
 */
public enum ShipmentStatus {
    AWAITING_SELLER,
    READY_TO_SHIP,
    OUT_FOR_DELIVERY,
    DELIVERED,
    DELIVERY_FAILED,
    CANCELLED,
    CREATED,
    IN_TRANSIT;

    private static final Map<ShipmentStatus, Set<ShipmentStatus>> NEXT = Map.of(
            AWAITING_SELLER, Set.of(READY_TO_SHIP, CANCELLED),
            READY_TO_SHIP, Set.of(OUT_FOR_DELIVERY, CANCELLED),
            OUT_FOR_DELIVERY, Set.of(DELIVERED, DELIVERY_FAILED),
            DELIVERY_FAILED, Set.of(READY_TO_SHIP, CANCELLED)
    );

    public boolean canMoveTo(ShipmentStatus next) {
        return NEXT.getOrDefault(this, Set.of()).contains(next);
    }

    /** Statuses in which the parcel is still with the seller: the only ones where a cancel is allowed. */
    public boolean isCancellable() {
        return canMoveTo(CANCELLED);
    }
}
