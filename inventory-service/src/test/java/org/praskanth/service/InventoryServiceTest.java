package org.praskanth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.TestData;
import org.praskanth.dto.InventoryResponse;
import org.praskanth.dto.ReservationItem;
import org.praskanth.entity.Inventory;
import org.praskanth.entity.OrderReservation;
import org.praskanth.entity.OrderReservation.ReservationStatus;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.outbox.InventoryReservationFailedFields;
import org.praskanth.outbox.InventoryReservedFields;
import org.praskanth.outbox.OutboxEventWriter;
import org.praskanth.repository.InventoryRepository;
import org.praskanth.repository.OrderReservationRepository;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private OrderReservationRepository orderReservationRepository;
    @Mock
    private OutboxEventWriter outboxEventWriter;

    private InventoryService service;

    @BeforeEach
    void setUp() {
        service = new InventoryService(inventoryRepository, orderReservationRepository, outboxEventWriter);
    }

    private void saveReturnsArgument() {
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- provisioning ----------

    @Test
    @DisplayName("provisionIfAbsent: creates a row with the initial stock when none exists")
    void provisionIfAbsent_new_savesRow() {
        when(inventoryRepository.existsByProductId("p1")).thenReturn(false);

        service.provisionIfAbsent("p1", "Shirt", 9L, 25);

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).save(captor.capture());
        Inventory saved = captor.getValue();
        assertThat(saved.getProductId()).isEqualTo("p1");
        assertThat(saved.getProductName()).isEqualTo("Shirt");
        assertThat(saved.getSellerId()).isEqualTo(9L);
        assertThat(saved.getAvailableQuantity()).isEqualTo(25);
        assertThat(saved.getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("provisionIfAbsent: negative initial stock is clamped to zero")
    void provisionIfAbsent_negativeStock_clampedToZero() {
        when(inventoryRepository.existsByProductId("p1")).thenReturn(false);

        service.provisionIfAbsent("p1", "Shirt", 9L, -5);

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).save(captor.capture());
        assertThat(captor.getValue().getAvailableQuantity()).isZero();
    }

    @Test
    @DisplayName("provisionIfAbsent: existing row is left untouched")
    void provisionIfAbsent_existing_noSave() {
        when(inventoryRepository.existsByProductId("p1")).thenReturn(true);

        service.provisionIfAbsent("p1", "Shirt", 9L, 25);

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("provisionIfAbsent (3 args): starts at zero stock")
    void provisionIfAbsent_threeArgs_startsAtZero() {
        when(inventoryRepository.existsByProductId("p1")).thenReturn(false);

        service.provisionIfAbsent("p1", "Shirt", 9L);

        ArgumentCaptor<Inventory> captor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).save(captor.capture());
        assertThat(captor.getValue().getAvailableQuantity()).isZero();
    }

    // ---------- queries ----------

    @Test
    @DisplayName("getByProductId: maps the entity to a response")
    void getByProductId_found_mapsResponse() {
        when(inventoryRepository.findByProductId("p1")).thenReturn(Optional.of(TestData.inventory("p1", 10, 3)));

        InventoryResponse response = service.getByProductId("p1");

        assertThat(response.getProductId()).isEqualTo("p1");
        assertThat(response.getProductName()).isEqualTo("Product p1");
        assertThat(response.getSellerId()).isEqualTo(7L);
        assertThat(response.getAvailableQuantity()).isEqualTo(10);
        assertThat(response.getReservedQuantity()).isEqualTo(3);
        assertThat(response.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("getByProductId: unknown product throws NotFoundException")
    void getByProductId_missing_throwsNotFound() {
        when(inventoryRepository.findByProductId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getByProductId("nope"))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("not available for this product");
    }

    @Test
    @DisplayName("getAll: maps every row")
    void getAll_mapsAllRows() {
        when(inventoryRepository.findAll())
                .thenReturn(List.of(TestData.inventory("a", 1, 0), TestData.inventory("b", 2, 0)));

        List<InventoryResponse> all = service.getAll();

        assertThat(all).extracting(InventoryResponse::getProductId).containsExactly("a", "b");
    }

    @Test
    @DisplayName("checkAvailability: true when available >= requested (boundary)")
    void checkAvailability_boundary_true() {
        when(inventoryRepository.findByProductId("p1")).thenReturn(Optional.of(TestData.inventory("p1", 5, 0)));

        assertThat(service.checkAvailability("p1", 5)).isTrue();
    }

    @Test
    @DisplayName("checkAvailability: false when one short")
    void checkAvailability_oneShort_false() {
        when(inventoryRepository.findByProductId("p1")).thenReturn(Optional.of(TestData.inventory("p1", 5, 0)));

        assertThat(service.checkAvailability("p1", 6)).isFalse();
    }

    @Test
    @DisplayName("checkAvailability: unknown product throws NotFoundException")
    void checkAvailability_missing_throwsNotFound() {
        when(inventoryRepository.findByProductId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.checkAvailability("nope", 1)).isInstanceOf(NotFoundException.class);
    }

    // ---------- restock / reserve / release / confirm ----------

    @Test
    @DisplayName("restock: adds to available stock and saves")
    void restock_addsToAvailable() {
        when(inventoryRepository.findByProductId("p1")).thenReturn(Optional.of(TestData.inventory("p1", 4, 1)));
        saveReturnsArgument();

        InventoryResponse response = service.restock("p1", 6);

        assertThat(response.getAvailableQuantity()).isEqualTo(10);
        assertThat(response.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("restock: unknown product throws NotFoundException and does not save")
    void restock_missing_throwsNotFound() {
        when(inventoryRepository.findByProductId("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.restock("nope", 1)).isInstanceOf(NotFoundException.class);
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("reserve: moves quantity from available to reserved")
    void reserve_enoughStock_movesToReserved() {
        when(inventoryRepository.findByProductId("p1")).thenReturn(Optional.of(TestData.inventory("p1", 10, 2)));
        saveReturnsArgument();

        InventoryResponse response = service.reserve("p1", 4);

        assertThat(response.getAvailableQuantity()).isEqualTo(6);
        assertThat(response.getReservedQuantity()).isEqualTo(6);
    }

    @Test
    @DisplayName("reserve: reserving exactly all available stock succeeds (boundary)")
    void reserve_exactlyAvailable_succeeds() {
        when(inventoryRepository.findByProductId("p1")).thenReturn(Optional.of(TestData.inventory("p1", 3, 0)));
        saveReturnsArgument();

        InventoryResponse response = service.reserve("p1", 3);

        assertThat(response.getAvailableQuantity()).isZero();
        assertThat(response.getReservedQuantity()).isEqualTo(3);
    }

    @Test
    @DisplayName("reserve: insufficient stock throws ConflictException and saves nothing")
    void reserve_insufficient_throwsConflict() {
        when(inventoryRepository.findByProductId("p1")).thenReturn(Optional.of(TestData.inventory("p1", 2, 0)));

        assertThatThrownBy(() -> service.reserve("p1", 3))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Insufficient stock")
                .hasMessageContaining("p1");
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("release: moves reserved stock back to available")
    void release_movesBackToAvailable() {
        when(inventoryRepository.findByProductId("p1")).thenReturn(Optional.of(TestData.inventory("p1", 5, 4)));
        saveReturnsArgument();

        InventoryResponse response = service.release("p1", 3);

        assertThat(response.getAvailableQuantity()).isEqualTo(8);
        assertThat(response.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("release: never releases more than is reserved")
    void release_moreThanReserved_cappedAtReserved() {
        when(inventoryRepository.findByProductId("p1")).thenReturn(Optional.of(TestData.inventory("p1", 5, 2)));
        saveReturnsArgument();

        InventoryResponse response = service.release("p1", 10);

        assertThat(response.getAvailableQuantity()).isEqualTo(7);
        assertThat(response.getReservedQuantity()).isZero();
    }

    @Test
    @DisplayName("confirm: removes reserved stock permanently, available unchanged")
    void confirm_removesReserved() {
        when(inventoryRepository.findByProductId("p1")).thenReturn(Optional.of(TestData.inventory("p1", 5, 4)));
        saveReturnsArgument();

        InventoryResponse response = service.confirm("p1", 3);

        assertThat(response.getAvailableQuantity()).isEqualTo(5);
        assertThat(response.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    @DisplayName("confirm: capped at the reserved quantity")
    void confirm_moreThanReserved_cappedAtReserved() {
        when(inventoryRepository.findByProductId("p1")).thenReturn(Optional.of(TestData.inventory("p1", 5, 2)));
        saveReturnsArgument();

        InventoryResponse response = service.confirm("p1", 9);

        assertThat(response.getAvailableQuantity()).isEqualTo(5);
        assertThat(response.getReservedQuantity()).isZero();
    }

    // ---------- optimistic-lock retry ----------

    @Test
    @DisplayName("withRetry: one optimistic-lock failure is retried with a freshly loaded row")
    void restock_optimisticLockOnce_retriesAndSucceeds() {
        when(inventoryRepository.findByProductId("p1"))
                .thenAnswer(inv -> Optional.of(TestData.inventory("p1", 10, 0)));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Inventory.class, "p1"))
                .thenAnswer(inv -> inv.getArgument(0));

        InventoryResponse response = service.restock("p1", 5);

        assertThat(response.getAvailableQuantity()).isEqualTo(15);
        verify(inventoryRepository, times(2)).findByProductId("p1");
        verify(inventoryRepository, times(2)).save(any(Inventory.class));
    }

    @Test
    @DisplayName("withRetry: persistent optimistic-lock failure ends in ConflictException after 3 attempts")
    void restock_optimisticLockAlways_throwsConflictAfterThreeAttempts() {
        when(inventoryRepository.findByProductId("p1"))
                .thenAnswer(inv -> Optional.of(TestData.inventory("p1", 10, 0)));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(Inventory.class, "p1"));

        assertThatThrownBy(() -> service.restock("p1", 5))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Many people are buying this at the same time");
        verify(inventoryRepository, times(3)).save(any(Inventory.class));
    }

    // ---------- reserveForOrder ----------

    @Test
    @DisplayName("reserveForOrder: all items reserved, ledger rows saved and InventoryReservedEvent written")
    void reserveForOrder_success_writesReservedEvent() {
        Inventory a = TestData.inventory("A", 10, 0);
        Inventory b = TestData.inventory("B", 5, 0);
        when(orderReservationRepository.existsByOrderId(100L)).thenReturn(false);
        when(inventoryRepository.findByProductId("A")).thenReturn(Optional.of(a));
        when(inventoryRepository.findByProductId("B")).thenReturn(Optional.of(b));
        saveReturnsArgument();

        service.reserveForOrder(100L, List.of(new ReservationItem("A", 2), new ReservationItem("B", 5)));

        assertThat(a.getAvailableQuantity()).isEqualTo(8);
        assertThat(a.getReservedQuantity()).isEqualTo(2);
        assertThat(b.getAvailableQuantity()).isZero();
        assertThat(b.getReservedQuantity()).isEqualTo(5);

        ArgumentCaptor<OrderReservation> captor = ArgumentCaptor.forClass(OrderReservation.class);
        verify(orderReservationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(OrderReservation::getStatus)
                .containsOnly(ReservationStatus.RESERVED);
        assertThat(captor.getAllValues())
                .extracting(OrderReservation::getProductId)
                .containsExactly("A", "B");

        ArgumentCaptor<Object> fields = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventWriter).write(eq("InventoryReservedEvent"), eq("inventory.reserved"), eq("100"), fields.capture());
        assertThat(fields.getValue()).isEqualTo(new InventoryReservedFields(100L));
    }

    @Test
    @DisplayName("reserveForOrder: redelivered order with existing reservations is a no-op")
    void reserveForOrder_alreadyProcessed_noop() {
        when(orderReservationRepository.existsByOrderId(100L)).thenReturn(true);

        service.reserveForOrder(100L, List.of(new ReservationItem("A", 2)));

        verifyNoInteractions(inventoryRepository);
        verifyNoInteractions(outboxEventWriter);
        verify(orderReservationRepository, never()).save(any(OrderReservation.class));
    }

    @Test
    @DisplayName("reserveForOrder: insufficient stock rolls back earlier items and writes a failure event")
    void reserveForOrder_insufficientStock_rollsBackAndWritesFailure() {
        Inventory a = TestData.inventory("A", 10, 0);
        Inventory b = TestData.inventory("B", 1, 0);
        when(orderReservationRepository.existsByOrderId(100L)).thenReturn(false);
        when(inventoryRepository.findByProductId("A")).thenReturn(Optional.of(a));
        when(inventoryRepository.findByProductId("B")).thenReturn(Optional.of(b));
        saveReturnsArgument();

        service.reserveForOrder(100L, List.of(new ReservationItem("A", 2), new ReservationItem("B", 5)));

        // A was reserved then released again: back to its original numbers
        assertThat(a.getAvailableQuantity()).isEqualTo(10);
        assertThat(a.getReservedQuantity()).isZero();
        // B untouched
        assertThat(b.getAvailableQuantity()).isEqualTo(1);
        assertThat(b.getReservedQuantity()).isZero();

        // the ledger row for A is saved twice, instance ends up RELEASED
        ArgumentCaptor<OrderReservation> captor = ArgumentCaptor.forClass(OrderReservation.class);
        verify(orderReservationRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(ReservationStatus.RELEASED);

        verify(outboxEventWriter, never()).write(eq("InventoryReservedEvent"), anyString(), anyString(), any());
        ArgumentCaptor<Object> fields = ArgumentCaptor.forClass(Object.class);
        verify(outboxEventWriter).write(eq("InventoryReservationFailedEvent"), eq("inventory.reservation-failed"),
                eq("100"), fields.capture());
        InventoryReservationFailedFields failed = (InventoryReservationFailedFields) fields.getValue();
        assertThat(failed.orderId()).isEqualTo(100L);
        assertThat(failed.reason()).contains("Insufficient stock").contains("B");
    }

    @Test
    @DisplayName("reserveForOrder: first item failing writes a failure event without any ledger rows")
    void reserveForOrder_firstItemFails_noLedgerRows() {
        when(orderReservationRepository.existsByOrderId(5L)).thenReturn(false);
        when(inventoryRepository.findByProductId("A")).thenReturn(Optional.of(TestData.inventory("A", 0, 0)));

        service.reserveForOrder(5L, List.of(new ReservationItem("A", 1)));

        verify(orderReservationRepository, never()).save(any(OrderReservation.class));
        verify(outboxEventWriter).write(eq("InventoryReservationFailedEvent"), eq("inventory.reservation-failed"),
                eq("5"), any(InventoryReservationFailedFields.class));
    }

    @Test
    @DisplayName("reserveForOrder: unknown product propagates NotFoundException and writes no event")
    void reserveForOrder_unknownProduct_propagatesNotFound() {
        when(orderReservationRepository.existsByOrderId(5L)).thenReturn(false);
        when(inventoryRepository.findByProductId("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reserveForOrder(5L, List.of(new ReservationItem("ghost", 1))))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(outboxEventWriter);
    }

    // ---------- releaseForOrder ----------

    @Test
    @DisplayName("releaseForOrder: releases every RESERVED line and marks it RELEASED")
    void releaseForOrder_releasesReservedLines() {
        Inventory a = TestData.inventory("A", 8, 2);
        OrderReservation r = TestData.reservation(100L, "A", 2, ReservationStatus.RESERVED);
        when(orderReservationRepository.findByOrderIdAndStatus(100L, ReservationStatus.RESERVED))
                .thenReturn(List.of(r));
        when(inventoryRepository.findByProductId("A")).thenReturn(Optional.of(a));
        saveReturnsArgument();

        service.releaseForOrder(100L);

        assertThat(a.getAvailableQuantity()).isEqualTo(10);
        assertThat(a.getReservedQuantity()).isZero();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(orderReservationRepository).save(r);
    }

    @Test
    @DisplayName("releaseForOrder: nothing reserved is a no-op")
    void releaseForOrder_nothingReserved_noop() {
        when(orderReservationRepository.findByOrderIdAndStatus(100L, ReservationStatus.RESERVED))
                .thenReturn(List.of());

        service.releaseForOrder(100L);

        verifyNoInteractions(inventoryRepository);
        verify(orderReservationRepository, never()).save(any(OrderReservation.class));
    }

    // ---------- confirmForOrder ----------

    @Test
    @DisplayName("confirmForOrder: deducts reserved stock and marks the line CONFIRMED")
    void confirmForOrder_confirmsReservedLines() {
        Inventory a = TestData.inventory("A", 8, 2);
        OrderReservation r = TestData.reservation(100L, "A", 2, ReservationStatus.RESERVED);
        when(orderReservationRepository.findByOrderIdAndStatus(100L, ReservationStatus.RESERVED))
                .thenReturn(List.of(r));
        when(inventoryRepository.findByProductId("A")).thenReturn(Optional.of(a));
        saveReturnsArgument();

        service.confirmForOrder(100L);

        assertThat(a.getAvailableQuantity()).isEqualTo(8);
        assertThat(a.getReservedQuantity()).isZero();
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(orderReservationRepository).save(r);
    }

    @Test
    @DisplayName("confirmForOrder: nothing reserved is a no-op")
    void confirmForOrder_nothingReserved_noop() {
        when(orderReservationRepository.findByOrderIdAndStatus(100L, ReservationStatus.RESERVED))
                .thenReturn(List.of());

        service.confirmForOrder(100L);

        verifyNoInteractions(inventoryRepository);
    }

    // ---------- restockReturnedForOrder ----------

    @Test
    @DisplayName("restockReturnedForOrder: CONFIRMED lines go back to available and become RELEASED")
    void restockReturnedForOrder_restocksConfirmedLines() {
        Inventory a = TestData.inventory("A", 3, 0);
        OrderReservation r = TestData.reservation(100L, "A", 2, ReservationStatus.CONFIRMED);
        when(orderReservationRepository.findByOrderIdAndStatus(100L, ReservationStatus.CONFIRMED))
                .thenReturn(List.of(r));
        when(inventoryRepository.findByProductId("A")).thenReturn(Optional.of(a));
        saveReturnsArgument();

        service.restockReturnedForOrder(100L);

        assertThat(a.getAvailableQuantity()).isEqualTo(5);
        assertThat(r.getStatus()).isEqualTo(ReservationStatus.RELEASED);
        verify(orderReservationRepository).save(r);
    }

    @Test
    @DisplayName("restockReturnedForOrder: no confirmed lines (e.g. redelivery) does nothing")
    void restockReturnedForOrder_noConfirmedLines_noop() {
        when(orderReservationRepository.findByOrderIdAndStatus(100L, ReservationStatus.CONFIRMED))
                .thenReturn(List.of());

        service.restockReturnedForOrder(100L);

        verifyNoInteractions(inventoryRepository);
        verify(orderReservationRepository, never()).save(any(OrderReservation.class));
    }
}
