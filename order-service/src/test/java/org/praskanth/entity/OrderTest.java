package org.praskanth.entity;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class OrderTest {

    @Test
    void onCreate_setsCreatedAtAndDeliveryDateSevenDaysLater() {
        Order order = new Order();
        Instant before = Instant.now();

        order.onCreate();

        assertThat(order.getCreatedAt()).isBetween(before, Instant.now());
        assertThat(order.getDeliveryDate()).isEqualTo(order.getCreatedAt().plus(7, ChronoUnit.DAYS));
    }

    @Test
    void onCreate_keepsPresetValues() {
        Order order = new Order();
        Instant created = Instant.parse("2024-01-01T00:00:00Z");
        Instant delivery = Instant.parse("2024-01-03T00:00:00Z");
        order.setCreatedAt(created);
        order.setDeliveryDate(delivery);

        order.onCreate();

        assertThat(order.getCreatedAt()).isEqualTo(created);
        assertThat(order.getDeliveryDate()).isEqualTo(delivery);
    }

    @Test
    void builder_defaultsToEmptyItemList() {
        Order order = Order.builder().build();
        assertThat(order.getItems()).isNotNull().isEmpty();
    }
}
