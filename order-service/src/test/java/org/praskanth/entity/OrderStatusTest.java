package org.praskanth.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderStatusTest {

    @Test
    @DisplayName("all lifecycle values exist, including the legacy PLACED and SHIPPED")
    void values_containsFullLifecycle() {
        assertThat(OrderStatus.values()).extracting(Enum::name).containsExactly(
                "PENDING", "AWAITING_PAYMENT", "PLACED", "CONFIRMED", "READY_TO_SHIP",
                "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED", "CANCELLED");
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    void valueOf_roundTripsEveryName(OrderStatus status) {
        assertThat(OrderStatus.valueOf(status.name())).isSameAs(status);
    }

    @Test
    void valueOf_unknownName_throws() {
        assertThatThrownBy(() -> OrderStatus.valueOf("REFUNDED")).isInstanceOf(IllegalArgumentException.class);
    }
}
