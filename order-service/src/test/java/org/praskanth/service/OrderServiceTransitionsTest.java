package org.praskanth.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.config.CartApiClient;
import org.praskanth.config.CouponApiClient;
import org.praskanth.dto.ReleaseRequest;
import org.praskanth.entity.Order;
import org.praskanth.entity.OrderStatus;
import org.praskanth.exception.NotFoundException;
import org.praskanth.outbox.OrderCancelledFields;
import org.praskanth.outbox.OutboxEventWriter;
import org.praskanth.repository.OrderRepository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/** Saga "scoreboard": every transition is guarded by the current status, so duplicate / out-of-order events are no-ops. */
@ExtendWith(MockitoExtension.class)
class OrderServiceTransitionsTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OutboxEventWriter outboxEventWriter;
    @Mock
    private CartApiClient cartApiClient;
    @Mock
    private CouponApiClient couponApiClient;
    @Mock
    private org.praskanth.notification.OrderNotificationPublisher notificationPublisher;

    @InjectMocks
    private OrderService service;

    @AfterEach
    void cleanSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Order stubOrder(OrderStatus status) {
        Order order = TestData.order(10L, 5L, 7L, status);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order));
        return order;
    }

    // ------------------------------------------------------------------ markAwaitingPayment

    @Test
    void markAwaitingPayment_pending_movesToAwaitingPayment() {
        Order order = stubOrder(OrderStatus.PENDING);

        service.markAwaitingPayment(10L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.AWAITING_PAYMENT);
        verify(orderRepository).save(order);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PENDING"})
    @DisplayName("markAwaitingPayment ignores a duplicate / late InventoryReserved for any non-PENDING order")
    void markAwaitingPayment_notPending_isNoOp(OrderStatus status) {
        Order order = stubOrder(status);

        service.markAwaitingPayment(10L);

        assertThat(order.getStatus()).isEqualTo(status);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void markAwaitingPayment_unknownOrder_notFound() {
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAwaitingPayment(10L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("couldn't find that order");
    }

    // ------------------------------------------------------------------ markConfirmed

    @ParameterizedTest
    @CsvSource({"RAZORPAY", "STRIPE", "COD"})
    @DisplayName("markConfirmed stores a supported payment method and clears the payment window")
    void markConfirmed_awaitingPayment_validMethod_confirms(String method) {
        Order order = stubOrder(OrderStatus.AWAITING_PAYMENT);
        order.setExpiresAt(Instant.now().plusSeconds(600));

        service.markConfirmed(10L, method);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentMethod()).isEqualTo(method);
        assertThat(order.getExpiresAt()).isNull();
        verify(orderRepository).save(order);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ONLINE", "PAYPAL", "cod", ""})
    @NullSource
    @DisplayName("markConfirmed still confirms but does not store an unsupported / missing payment method")
    void markConfirmed_unsupportedMethod_confirmsWithoutMethod(String method) {
        Order order = stubOrder(OrderStatus.AWAITING_PAYMENT);

        service.markConfirmed(10L, method);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getPaymentMethod()).isNull();
        verify(orderRepository).save(order);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"AWAITING_PAYMENT"})
    @DisplayName("markConfirmed ignores payment events for orders that are not AWAITING_PAYMENT (e.g. already cancelled)")
    void markConfirmed_notAwaitingPayment_isNoOp(OrderStatus status) {
        Order order = stubOrder(status);

        service.markConfirmed(10L, "STRIPE");

        assertThat(order.getStatus()).isEqualTo(status);
        assertThat(order.getPaymentMethod()).isNull();
        verify(orderRepository, never()).save(any());
    }

    @Test
    void markConfirmed_unknownOrder_notFound() {
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markConfirmed(10L, "COD")).isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------ markShipped (legacy)

    @Test
    void markShipped_confirmed_movesToShipped() {
        Order order = stubOrder(OrderStatus.CONFIRMED);

        service.markShipped(10L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository).save(order);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"CONFIRMED"})
    void markShipped_notConfirmed_isNoOp(OrderStatus status) {
        Order order = stubOrder(status);

        service.markShipped(10L);

        assertThat(order.getStatus()).isEqualTo(status);
        verify(orderRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ markCancelled

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "AWAITING_PAYMENT", "PLACED", "CONFIRMED", "READY_TO_SHIP"})
    @DisplayName("markCancelled cancels any order that has not left the warehouse and publishes OrderCancelledEvent")
    void markCancelled_cancellable_cancelsAndWritesOutbox(OrderStatus status) {
        Order order = stubOrder(status);
        order.setExpiresAt(Instant.now().plusSeconds(60));

        service.markCancelled(10L, "Out of stock");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("Out of stock");
        assertThat(order.getExpiresAt()).isNull();
        verify(orderRepository).save(order);
        ArgumentCaptor<Object> fields = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventWriter).write(eq("OrderCancelledEvent"), eq("order.cancelled"), eq("10"), fields.capture());
        OrderCancelledFields f = (OrderCancelledFields) fields.getValue();
        assertThat(f.orderId()).isEqualTo(10L);
        assertThat(f.userId()).isEqualTo(5L);
        assertThat(f.reason()).isEqualTo("Out of stock");
        assertThat(f.cancelledAt()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "SHIPPED", "OUT_FOR_DELIVERY", "DELIVERED"})
    @DisplayName("markCancelled is a no-op for orders already cancelled, on the road or delivered")
    void markCancelled_notCancellable_isNoOp(OrderStatus status) {
        Order order = stubOrder(status);

        service.markCancelled(10L, "late failure event");

        assertThat(order.getStatus()).isEqualTo(status);
        assertThat(order.getCancellationReason()).isNull();
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    void markCancelled_unknownOrder_notFound() {
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markCancelled(10L, "x")).isInstanceOf(NotFoundException.class);
        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    @DisplayName("duplicate cancellation: the second call does not publish a second event")
    void markCancelled_twice_publishesOnce() {
        stubOrder(OrderStatus.PENDING);

        service.markCancelled(10L, "first");
        service.markCancelled(10L, "second");

        verify(outboxEventWriter, org.mockito.Mockito.times(1)).write(anyString(), anyString(), anyString(), any());
        verify(orderRepository, org.mockito.Mockito.times(1)).save(any(Order.class));
    }

    // ------------------------------------------------------------------ coupon release on full-checkout cancel

    @Test
    void markCancelled_lastOrderOfCheckoutWithCoupon_releasesCoupon() {
        Order order = stubOrder(OrderStatus.AWAITING_PAYMENT);
        order.setCheckoutRef("chk-1");
        order.setCouponCode("SAVE10");
        Order sibling = TestData.order(11L, 5L, 8L, OrderStatus.CANCELLED);
        when(orderRepository.findByCheckoutRef("chk-1")).thenReturn(List.of(order, sibling));

        service.markCancelled(10L, "x");

        ArgumentCaptor<ReleaseRequest> release = ArgumentCaptor.forClass(ReleaseRequest.class);
        verify(couponApiClient).release(release.capture());
        assertThat(release.getValue().checkoutRef()).isEqualTo("chk-1");
    }

    @Test
    void markCancelled_siblingStillActive_keepsCoupon() {
        Order order = stubOrder(OrderStatus.AWAITING_PAYMENT);
        order.setCheckoutRef("chk-1");
        order.setCouponCode("SAVE10");
        Order sibling = TestData.order(11L, 5L, 8L, OrderStatus.CONFIRMED);
        when(orderRepository.findByCheckoutRef("chk-1")).thenReturn(List.of(order, sibling));

        service.markCancelled(10L, "x");

        verify(couponApiClient, never()).release(any());
    }

    @Test
    void markCancelled_orderWithoutCoupon_doesNotLookAtCheckout() {
        Order order = stubOrder(OrderStatus.PENDING);
        order.setCheckoutRef("chk-1");

        service.markCancelled(10L, "x");

        verify(orderRepository, never()).findByCheckoutRef(anyString());
        verifyNoInteractions(couponApiClient);
    }

    @Test
    void markCancelled_orderWithoutCheckoutRef_doesNotLookAtCheckout() {
        Order order = stubOrder(OrderStatus.PENDING);
        order.setCouponCode("SAVE10");

        service.markCancelled(10L, "x");

        verify(orderRepository, never()).findByCheckoutRef(anyString());
        verifyNoInteractions(couponApiClient);
    }

    @Test
    void markCancelled_couponReleaseFails_cancellationStillSucceeds() {
        Order order = stubOrder(OrderStatus.PENDING);
        order.setCheckoutRef("chk-1");
        order.setCouponCode("SAVE10");
        when(orderRepository.findByCheckoutRef("chk-1")).thenReturn(List.of(order));
        doThrow(new RuntimeException("coupon down")).when(couponApiClient).release(any(ReleaseRequest.class));

        service.markCancelled(10L, "x");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void markCancelled_inTransaction_releasesCouponOnlyAfterCommit() {
        Order order = stubOrder(OrderStatus.PENDING);
        order.setCheckoutRef("chk-1");
        order.setCouponCode("SAVE10");
        when(orderRepository.findByCheckoutRef("chk-1")).thenReturn(List.of(order));
        TransactionSynchronizationManager.initSynchronization();

        service.markCancelled(10L, "x");

        verify(couponApiClient, never()).release(any());
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
            sync.afterCommit();
        }
        verify(couponApiClient).release(any(ReleaseRequest.class));
    }

    // ------------------------------------------------------------------ applyShipmentStatus

    @ParameterizedTest
    @CsvSource({
            "CONFIRMED, READY_TO_SHIP, READY_TO_SHIP",
            "OUT_FOR_DELIVERY, READY_TO_SHIP, READY_TO_SHIP",
            "CONFIRMED, OUT_FOR_DELIVERY, OUT_FOR_DELIVERY",
            "READY_TO_SHIP, OUT_FOR_DELIVERY, OUT_FOR_DELIVERY",
            "OUT_FOR_DELIVERY, DELIVERED, DELIVERED"
    })
    @DisplayName("applyShipmentStatus follows the legal shipment progress")
    void applyShipmentStatus_legalTransitions(OrderStatus from, String shipmentStatus, OrderStatus expected) {
        Order order = stubOrder(from);

        service.applyShipmentStatus(10L, shipmentStatus, "note");

        assertThat(order.getStatus()).isEqualTo(expected);
        verify(orderRepository).save(order);
    }

    @ParameterizedTest
    @CsvSource({
            "PENDING, READY_TO_SHIP",
            "AWAITING_PAYMENT, READY_TO_SHIP",
            "CANCELLED, READY_TO_SHIP",
            "DELIVERED, READY_TO_SHIP",
            "PENDING, OUT_FOR_DELIVERY",
            "CANCELLED, OUT_FOR_DELIVERY",
            "DELIVERED, OUT_FOR_DELIVERY",
            "CONFIRMED, DELIVERED",
            "READY_TO_SHIP, DELIVERED",
            "CANCELLED, DELIVERED",
            "PENDING, DELIVERED"
    })
    @DisplayName("applyShipmentStatus ignores out-of-order events that would skip or reverse the lifecycle")
    void applyShipmentStatus_illegalTransitions_areIgnored(OrderStatus from, String shipmentStatus) {
        Order order = stubOrder(from);

        service.applyShipmentStatus(10L, shipmentStatus, "note");

        assertThat(order.getStatus()).isEqualTo(from);
        verify(orderRepository, never()).save(any());
    }

    @ParameterizedTest
    @CsvSource({
            "READY_TO_SHIP, READY_TO_SHIP",
            "OUT_FOR_DELIVERY, OUT_FOR_DELIVERY",
            "DELIVERED, DELIVERED"
    })
    @DisplayName("applyShipmentStatus: a duplicate event for the status the order already has changes nothing")
    void applyShipmentStatus_duplicate_isNoOp(OrderStatus current, String shipmentStatus) {
        Order order = stubOrder(current);

        service.applyShipmentStatus(10L, shipmentStatus, null);

        assertThat(order.getStatus()).isEqualTo(current);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void applyShipmentStatus_cancelled_cancelsWithNoteAsReason() {
        Order order = stubOrder(OrderStatus.CONFIRMED);

        service.applyShipmentStatus(10L, "CANCELLED", "Seller cancelled");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancellationReason()).isEqualTo("Seller cancelled");
        verify(outboxEventWriter).write(eq("OrderCancelledEvent"), eq("order.cancelled"), eq("10"), any());
    }

    @Test
    @DisplayName("a parcel that could not be delivered may cancel an order that was OUT_FOR_DELIVERY")
    void applyShipmentStatus_cancelledFromOutForDelivery_isAllowed() {
        Order order = stubOrder(OrderStatus.OUT_FOR_DELIVERY);

        service.applyShipmentStatus(10L, "CANCELLED", "Customer unreachable");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(outboxEventWriter).write(eq("OrderCancelledEvent"), anyString(), anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    @NullSource
    void applyShipmentStatus_cancelledWithoutNote_usesDefaultReason(String note) {
        Order order = stubOrder(OrderStatus.CONFIRMED);

        service.applyShipmentStatus(10L, "CANCELLED", note);

        assertThat(order.getCancellationReason()).isEqualTo("Cancelled");
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"CANCELLED", "DELIVERED", "SHIPPED"})
    void applyShipmentStatus_cancelledForFinishedOrder_isNoOp(OrderStatus status) {
        Order order = stubOrder(status);

        service.applyShipmentStatus(10L, "CANCELLED", "late");

        assertThat(order.getStatus()).isEqualTo(status);
        verifyNoInteractions(outboxEventWriter);
    }

    @ParameterizedTest
    @ValueSource(strings = {"AWAITING_SELLER", "DELIVERY_FAILED", "SOMETHING_NEW"})
    void applyShipmentStatus_statusWithoutOrderEffect_touchesNothing(String shipmentStatus) {
        service.applyShipmentStatus(10L, shipmentStatus, "n");

        verifyNoInteractions(orderRepository, outboxEventWriter);
    }

    @Test
    void applyShipmentStatus_unknownOrder_notFound() {
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.applyShipmentStatus(10L, "DELIVERED", null))
                .isInstanceOf(NotFoundException.class);
    }
}
