package org.praskanth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.config.CartApiClient;
import org.praskanth.config.CouponApiClient;
import org.praskanth.entity.Order;
import org.praskanth.entity.OrderStatus;
import org.praskanth.notification.OrderNotificationPublisher;
import org.praskanth.notification.OrderStage;
import org.praskanth.outbox.OutboxEventWriter;
import org.praskanth.repository.OrderRepository;

import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Which order changes produce a customer e-mail, and which (duplicates, out-of-order events) must stay silent. */
@ExtendWith(MockitoExtension.class)
class OrderServiceNotificationsTest {

    @Mock private OrderRepository orderRepository;
    @Mock private OutboxEventWriter outboxEventWriter;
    @Mock private CartApiClient cartApiClient;
    @Mock private CouponApiClient couponApiClient;
    @Mock private OrderNotificationPublisher notificationPublisher;

    @InjectMocks
    private OrderService service;

    private Order stubOrder(OrderStatus status) {
        Order order = TestData.order(10L, 5L, 7L, status);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    @DisplayName("payment completed -> 'order placed' e-mail")
    void confirmed_sendsOrderPlaced() {
        Order order = stubOrder(OrderStatus.AWAITING_PAYMENT);

        service.markConfirmed(10L, "COD");

        verify(notificationPublisher).publish(order, OrderStage.ORDER_PLACED, null);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"AWAITING_PAYMENT"})
    @DisplayName("a duplicate / late payment event does not send a second 'order placed' e-mail")
    void confirmed_whenNotAwaitingPayment_sendsNothing(OrderStatus status) {
        stubOrder(status);

        service.markConfirmed(10L, "COD");

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    @DisplayName("seller packs the parcel -> 'packed' e-mail")
    void readyToShip_fromConfirmed_sendsPacked() {
        Order order = stubOrder(OrderStatus.CONFIRMED);

        service.applyShipmentStatus(10L, "READY_TO_SHIP", null);

        verify(notificationPublisher).publish(order, OrderStage.PACKED, null);
    }

    @Test
    @DisplayName("a failed delivery attempt puts the parcel back in the pool: NOT announced as 'packed' again")
    void readyToShip_afterFailedDelivery_isSilent() {
        stubOrder(OrderStatus.OUT_FOR_DELIVERY);

        service.applyShipmentStatus(10L, "READY_TO_SHIP", null);

        verifyNoInteractions(notificationPublisher);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CONFIRMED", "READY_TO_SHIP"})
    @DisplayName("delivery partner picks the parcel up -> 'out for delivery' e-mail")
    void outForDelivery_sendsPickedUp(OrderStatus from) {
        Order order = stubOrder(from);

        service.applyShipmentStatus(10L, "OUT_FOR_DELIVERY", null);

        verify(notificationPublisher).publish(order, OrderStage.OUT_FOR_DELIVERY, null);
    }

    @Test
    void delivered_sendsDelivered() {
        Order order = stubOrder(OrderStatus.OUT_FOR_DELIVERY);

        service.applyShipmentStatus(10L, "DELIVERED", null);

        verify(notificationPublisher).publish(order, OrderStage.DELIVERED, null);
    }

    @Test
    @DisplayName("the same event delivered twice: the second one changes nothing and sends nothing")
    void duplicateDelivered_isSilent() {
        stubOrder(OrderStatus.DELIVERED);

        service.applyShipmentStatus(10L, "DELIVERED", null);

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    @DisplayName("a jump the state machine does not allow (CONFIRMED -> DELIVERED) sends nothing")
    void illegalJump_isSilent() {
        stubOrder(OrderStatus.CONFIRMED);

        service.applyShipmentStatus(10L, "DELIVERED", null);

        verifyNoInteractions(notificationPublisher);
    }

    @Test
    @DisplayName("cancelled -> 'cancelled' e-mail carrying the reason")
    void cancelled_sendsCancelledWithReason() {
        Order order = stubOrder(OrderStatus.CONFIRMED);

        service.markCancelled(10L, "Cancelled by seller");

        verify(notificationPublisher).publish(order, OrderStage.CANCELLED, "Cancelled by seller");
    }

    @Test
    @DisplayName("inventory could not reserve -> 'out of stock' e-mail")
    void outOfStock_sendsOutOfStock() {
        Order order = stubOrder(OrderStatus.PENDING);

        service.markCancelledOutOfStock(10L, "Insufficient stock");

        verify(notificationPublisher).publish(order, OrderStage.OUT_OF_STOCK, "Insufficient stock");
    }

    @Test
    @DisplayName("a parcel the agent could not deliver is cancelled -> 'cancelled' e-mail")
    void shipmentCancelled_sendsCancelled() {
        Order order = stubOrder(OrderStatus.OUT_FOR_DELIVERY);

        service.applyShipmentStatus(10L, "CANCELLED", "Customer unreachable");

        verify(notificationPublisher).publish(order, OrderStage.CANCELLED, "Customer unreachable");
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "DELIVERED", "SHIPPED"})
    @DisplayName("cancelling an already finished order sends nothing")
    void cancel_whenAlreadyFinal_isSilent(OrderStatus status) {
        stubOrder(status);

        service.markCancelled(10L, "late");

        verifyNoInteractions(notificationPublisher);
    }
}
