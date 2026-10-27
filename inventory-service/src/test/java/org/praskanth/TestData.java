package org.praskanth;

import org.praskanth.entity.Inventory;
import org.praskanth.entity.OrderReservation;

/** Small shared builders for inventory-service tests. */
public final class TestData {

    private TestData() {
    }

    public static Inventory inventory(String productId, int available, int reserved) {
        return Inventory.builder()
                .productId(productId)
                .productName("Product " + productId)
                .sellerId(7L)
                .availableQuantity(available)
                .reservedQuantity(reserved)
                .build();
    }

    public static OrderReservation reservation(Long orderId, String productId, int quantity,
                                               OrderReservation.ReservationStatus status) {
        return OrderReservation.builder()
                .orderId(orderId)
                .productId(productId)
                .quantity(quantity)
                .status(status)
                .build();
    }
}
