package org.praskanth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.config.CartApiClient;
import org.praskanth.config.CouponApiClient;
import org.praskanth.dto.OrderResponse;
import org.praskanth.entity.Order;
import org.praskanth.entity.OrderStatus;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.outbox.OutboxEventWriter;
import org.praskanth.repository.OrderRepository;
import org.praskanth.security.gateway.GatewayUser;

import java.time.Instant;
import java.util.Collection;
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

@ExtendWith(MockitoExtension.class)
class OrderServiceCustomerActionsAndQueriesTest {

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

    private Order order(Long id, Long userId, Long sellerId, OrderStatus status) {
        return TestData.order(id, userId, sellerId, status);
    }

    // ------------------------------------------------------------------ queries

    @Test
    void getById_found_mapsAllFields() {
        Order o = order(10L, 5L, 7L, OrderStatus.CONFIRMED);
        o.setCancellationReason(null);
        o.setCouponCode("SAVE10");
        o.setCheckoutRef("chk");
        o.setPaymentMethod("COD");
        o.setShippingAddress("addr");
        o.getItems().add(TestData.orderItem("P1", "V1", 3, "9.99"));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(o));

        OrderResponse r = service.getById(10L);

        assertThat(r.getId()).isEqualTo(10L);
        assertThat(r.getUserId()).isEqualTo(5L);
        assertThat(r.getSellerId()).isEqualTo(7L);
        assertThat(r.getStatus()).isEqualTo("CONFIRMED");
        assertThat(r.getCouponCode()).isEqualTo("SAVE10");
        assertThat(r.getCheckoutRef()).isEqualTo("chk");
        assertThat(r.getPaymentMethod()).isEqualTo("COD");
        assertThat(r.getShippingAddress()).isEqualTo("addr");
        assertThat(r.getItems()).hasSize(1);
        assertThat(r.getItems().get(0).getProductId()).isEqualTo("P1");
        assertThat(r.getItems().get(0).getVariantId()).isEqualTo("V1");
        assertThat(r.getItems().get(0).getQuantity()).isEqualTo(3);
        assertThat(r.getItems().get(0).getUnitPrice()).isEqualByComparingTo("9.99");
    }

    @Test
    void getById_missing_notFound() {
        when(orderRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(1L))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("We couldn't find that order.");
    }

    @Test
    void getByIdForCaller_owner_allowed() {
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(10L, 5L, 7L, OrderStatus.PENDING)));

        assertThat(service.getByIdForCaller(10L, TestData.customer(5L)).getId()).isEqualTo(10L);
    }

    @Test
    void getByIdForCaller_sellerOfTheOrder_allowed() {
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(10L, 5L, 7L, OrderStatus.PENDING)));

        assertThat(service.getByIdForCaller(10L, TestData.customer(7L)).getId()).isEqualTo(10L);
    }

    @Test
    void getByIdForCaller_admin_allowed() {
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(10L, 5L, 7L, OrderStatus.PENDING)));

        assertThat(service.getByIdForCaller(10L, TestData.admin(1L)).getId()).isEqualTo(10L);
    }

    @Test
    void getByIdForCaller_otherCustomer_forbidden() {
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(10L, 5L, 7L, OrderStatus.PENDING)));

        assertThatThrownBy(() -> service.getByIdForCaller(10L, TestData.customer(99L)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("an order without a seller id is not readable by an unrelated customer")
    void getByIdForCaller_orderWithoutSeller_otherCustomerForbidden() {
        when(orderRepository.findById(10L)).thenReturn(Optional.of(order(10L, 5L, null, OrderStatus.PENDING)));

        assertThatThrownBy(() -> service.getByIdForCaller(10L, TestData.customer(99L)))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void getByIdForCaller_missing_notFound() {
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByIdForCaller(10L, TestData.customer(5L)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getByCheckoutRef_mapsEveryOrder() {
        when(orderRepository.findByCheckoutRef("c")).thenReturn(List.of(
                order(1L, 5L, 7L, OrderStatus.PENDING), order(2L, 5L, 8L, OrderStatus.PENDING)));

        assertThat(service.getByCheckoutRef("c")).extracting(OrderResponse::getId).containsExactly(1L, 2L);
    }

    @Test
    void getByCheckoutRef_none_emptyList() {
        when(orderRepository.findByCheckoutRef("c")).thenReturn(List.of());

        assertThat(service.getByCheckoutRef("c")).isEmpty();
    }

    @Test
    void getByUser_returnsOrdersOfThatUser() {
        when(orderRepository.findByUserId(5L)).thenReturn(List.of(order(1L, 5L, 7L, OrderStatus.DELIVERED)));

        List<OrderResponse> result = service.getByUser(5L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo("DELIVERED");
    }

    @Test
    void getBySeller_returnsOrdersOfThatSeller() {
        when(orderRepository.findBySellerId(7L)).thenReturn(List.of(
                order(1L, 5L, 7L, OrderStatus.PENDING), order(2L, 6L, 7L, OrderStatus.CANCELLED)));

        assertThat(service.getBySeller(7L)).hasSize(2);
    }

    // ------------------------------------------------------------------ cancelUnpaidByCustomer

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "AWAITING_PAYMENT"})
    void cancelUnpaidByCustomer_unpaidOwnOrder_cancels(OrderStatus status) {
        Order o = order(10L, 5L, 7L, status);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(o));

        service.cancelUnpaidByCustomer(10L, TestData.customer(5L));

        assertThat(o.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(o.getCancellationReason()).isEqualTo("Cancelled by customer before payment");
        verify(outboxEventWriter).write(eq("OrderCancelledEvent"), eq("order.cancelled"), eq("10"), any());
    }

    @Test
    void cancelUnpaidByCustomer_otherCustomersOrder_forbiddenAndUnchanged() {
        Order o = order(10L, 5L, 7L, OrderStatus.PENDING);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.cancelUnpaidByCustomer(10L, TestData.customer(99L)))
                .isInstanceOf(ForbiddenOperationException.class);

        assertThat(o.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(outboxEventWriter);
    }

    @ParameterizedTest
    @EnumSource(value = OrderStatus.class, mode = EnumSource.Mode.EXCLUDE, names = {"PENDING", "AWAITING_PAYMENT"})
    @DisplayName("a paid / shipped / finished order cannot be cancelled by the customer here (409)")
    void cancelUnpaidByCustomer_paidOrFinished_conflict(OrderStatus status) {
        Order o = order(10L, 5L, 7L, status);
        when(orderRepository.findById(10L)).thenReturn(Optional.of(o));

        assertThatThrownBy(() -> service.cancelUnpaidByCustomer(10L, TestData.customer(5L)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining(status.name());

        assertThat(o.getStatus()).isEqualTo(status);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelUnpaidByCustomer_unknownOrder_notFound() {
        when(orderRepository.findById(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancelUnpaidByCustomer(10L, TestData.customer(5L)))
                .isInstanceOf(NotFoundException.class);
    }

    // ------------------------------------------------------------------ cancelCheckoutByCustomer

    @Test
    void cancelCheckoutByCustomer_unknownCheckout_notFound() {
        when(orderRepository.findByCheckoutRef("c")).thenReturn(List.of());

        assertThatThrownBy(() -> service.cancelCheckoutByCustomer("c", TestData.customer(5L)))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("c");
    }

    @Test
    @DisplayName("cancelling a checkout that belongs to someone else cancels nothing, even if the first order is the caller's")
    void cancelCheckoutByCustomer_anyForeignOrder_forbiddenBeforeAnyCancellation() {
        Order mine = order(10L, 5L, 7L, OrderStatus.PENDING);
        Order foreign = order(11L, 99L, 8L, OrderStatus.PENDING);
        when(orderRepository.findByCheckoutRef("c")).thenReturn(List.of(mine, foreign));

        assertThatThrownBy(() -> service.cancelCheckoutByCustomer("c", TestData.customer(5L)))
                .isInstanceOf(ForbiddenOperationException.class);

        assertThat(mine.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
        verifyNoInteractions(outboxEventWriter);
    }

    @Test
    @DisplayName("cancelCheckoutByCustomer cancels only the still-unpaid orders and leaves paid ones alone")
    void cancelCheckoutByCustomer_mixedStatuses_cancelsOnlyUnpaid() {
        Order pending = order(10L, 5L, 7L, OrderStatus.PENDING);
        Order awaiting = order(11L, 5L, 8L, OrderStatus.AWAITING_PAYMENT);
        Order confirmed = order(12L, 5L, 9L, OrderStatus.CONFIRMED);
        when(orderRepository.findByCheckoutRef("c")).thenReturn(List.of(pending, awaiting, confirmed));
        when(orderRepository.findById(10L)).thenReturn(Optional.of(pending));
        when(orderRepository.findById(11L)).thenReturn(Optional.of(awaiting));

        service.cancelCheckoutByCustomer("c", TestData.customer(5L));

        assertThat(pending.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(awaiting.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(confirmed.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(outboxEventWriter).write(eq("OrderCancelledEvent"), anyString(), eq("10"), any());
        verify(outboxEventWriter).write(eq("OrderCancelledEvent"), anyString(), eq("11"), any());
        verify(outboxEventWriter, never()).write(anyString(), anyString(), eq("12"), any());
    }

    // ------------------------------------------------------------------ findExpiredUnpaidOrderIds

    @Test
    @DisplayName("findExpiredUnpaidOrderIds asks for PENDING and AWAITING_PAYMENT orders whose expiry is before now")
    @SuppressWarnings("unchecked")
    void findExpiredUnpaidOrderIds_returnsIdsAndQueriesUnpaidStatuses() {
        when(orderRepository.findTop100ByStatusInAndExpiresAtBefore(any(Collection.class), any(Instant.class)))
                .thenReturn(List.of(order(1L, 5L, 7L, OrderStatus.PENDING), order(2L, 5L, 7L, OrderStatus.AWAITING_PAYMENT)));
        Instant before = Instant.now();

        List<Long> ids = service.findExpiredUnpaidOrderIds();

        assertThat(ids).containsExactly(1L, 2L);
        ArgumentCaptor<Collection<OrderStatus>> statuses = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Instant> now = ArgumentCaptor.forClass(Instant.class);
        verify(orderRepository).findTop100ByStatusInAndExpiresAtBefore(statuses.capture(), now.capture());
        assertThat(statuses.getValue()).containsExactlyInAnyOrder(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT);
        assertThat(now.getValue()).isBetween(before, Instant.now());
    }

    @Test
    void findExpiredUnpaidOrderIds_nothingExpired_empty() {
        when(orderRepository.findTop100ByStatusInAndExpiresAtBefore(any(), any())).thenReturn(List.of());

        assertThat(service.findExpiredUnpaidOrderIds()).isEmpty();
    }
}
