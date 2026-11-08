package org.praskanth.entity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The shipment life cycle is a small state machine: every legal and illegal move is pinned down here. */
class ShipmentStatusTest {

    @ParameterizedTest(name = "{0} -> {1} is allowed")
    @CsvSource({
            "AWAITING_SELLER, READY_TO_SHIP",
            "AWAITING_SELLER, CANCELLED",
            "READY_TO_SHIP, OUT_FOR_DELIVERY",
            "READY_TO_SHIP, CANCELLED",
            "OUT_FOR_DELIVERY, DELIVERED",
            "OUT_FOR_DELIVERY, DELIVERY_FAILED",
            "DELIVERY_FAILED, READY_TO_SHIP",
            "DELIVERY_FAILED, CANCELLED"
    })
    void legalMoves(ShipmentStatus from, ShipmentStatus to) {
        assertThat(from.canMoveTo(to)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} is refused")
    @CsvSource({
            "AWAITING_SELLER, OUT_FOR_DELIVERY",
            "AWAITING_SELLER, DELIVERED",
            "READY_TO_SHIP, DELIVERED",
            "READY_TO_SHIP, AWAITING_SELLER",
            "OUT_FOR_DELIVERY, CANCELLED",
            "OUT_FOR_DELIVERY, READY_TO_SHIP",
            "DELIVERY_FAILED, DELIVERED"
    })
    void illegalMoves(ShipmentStatus from, ShipmentStatus to) {
        assertThat(from.canMoveTo(to)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = ShipmentStatus.class, names = {"DELIVERED", "CANCELLED", "CREATED", "IN_TRANSIT"})
    void terminalAndLegacyStatesCannotMoveAnywhere(ShipmentStatus from) {
        for (ShipmentStatus to : ShipmentStatus.values()) {
            assertThat(from.canMoveTo(to)).as("%s -> %s", from, to).isFalse();
        }
    }

    @ParameterizedTest
    @EnumSource(ShipmentStatus.class)
    void aStatusCanNeverMoveToItself(ShipmentStatus s) {
        assertThat(s.canMoveTo(s)).isFalse();
    }

    @Test
    void onlyParcelsStillWithTheSellerOrFailedCanBeCancelled() {
        assertThat(ShipmentStatus.AWAITING_SELLER.isCancellable()).isTrue();
        assertThat(ShipmentStatus.READY_TO_SHIP.isCancellable()).isTrue();
        assertThat(ShipmentStatus.DELIVERY_FAILED.isCancellable()).isTrue();
        assertThat(ShipmentStatus.OUT_FOR_DELIVERY.isCancellable()).isFalse();
        assertThat(ShipmentStatus.DELIVERED.isCancellable()).isFalse();
        assertThat(ShipmentStatus.CANCELLED.isCancellable()).isFalse();
    }
}
