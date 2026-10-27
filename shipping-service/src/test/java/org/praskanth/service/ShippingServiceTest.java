package org.praskanth.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.dto.OrderInfo;
import org.praskanth.dto.ShipmentDtos.ShipmentView;
import org.praskanth.entity.Shipment;
import org.praskanth.entity.ShipmentEvent;
import org.praskanth.entity.ShipmentStatus;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.outbox.OutboxEventWriter;
import org.praskanth.outbox.ShipmentStatusFields;
import org.praskanth.repository.ShipmentEventRepository;
import org.praskanth.repository.ShipmentRepository;
import org.praskanth.security.gateway.GatewayUser;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShippingServiceTest {

    static final long ORDER = 500L;
    static final long CUSTOMER = 7L;
    static final long SELLER = 99L;
    static final long AGENT = 55L;

    @Mock ShipmentRepository shipments;
    @Mock ShipmentEventRepository timeline;
    @Mock OrderClient orderClient;
    @Mock OutboxEventWriter outbox;
    @Mock OtpService otp;

    @InjectMocks ShippingService service;

    private static GatewayUser user(long id, String role) {
        return new GatewayUser(id, "u" + id + "@x.com", Set.of(role));
    }

    private static Shipment shipment(ShipmentStatus status) {
        return Shipment.builder().id(1L).orderId(ORDER).userId(CUSTOMER).sellerId(SELLER)
                .status(status).trackingNumber("TRK1").carrier("eShop Logistics")
                .paymentMethod("STRIPE").cashToCollect(BigDecimal.ZERO).build();
    }

    private Shipment found(ShipmentStatus status) {
        Shipment s = shipment(status);
        when(shipments.findByOrderId(ORDER)).thenReturn(Optional.of(s));
        return s;
    }

    private static OrderInfo order(String status, String amount) {
        OrderInfo o = new OrderInfo();
        o.setId(ORDER);
        o.setUserId(CUSTOMER);
        o.setSellerId(SELLER);
        o.setStatus(status);
        o.setTotalAmount(new BigDecimal(amount));
        o.setShippingAddress("Asha, 12 MG Road, Bengaluru, KA - 560001, India");
        return o;
    }

    // ------------------------------------------------------------ creation

    @Test
    @DisplayName("createShipmentForOrder: online payment -> AWAITING_SELLER, nothing to collect")
    void createsShipmentForPaidOrder() {
        when(shipments.existsByOrderId(ORDER)).thenReturn(false);
        when(orderClient.get(ORDER)).thenReturn(order("CONFIRMED", "250.00"));
        when(shipments.save(any(Shipment.class))).thenAnswer(inv -> {
            Shipment s = inv.getArgument(0);
            s.setId(1L);
            return s;
        });

        service.createShipmentForOrder(ORDER, "STRIPE");

        ArgumentCaptor<Shipment> cap = ArgumentCaptor.forClass(Shipment.class);
        verify(shipments).save(cap.capture());
        Shipment s = cap.getValue();
        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.AWAITING_SELLER);
        assertThat(s.getTrackingNumber()).matches("TRK\\d{9}");
        assertThat(s.getCashToCollect()).isEqualByComparingTo("0");
        assertThat(s.getShippingPincode()).isEqualTo("560001");
        assertThat(s.getUserId()).isEqualTo(CUSTOMER);
        assertThat(s.getSellerId()).isEqualTo(SELLER);
        verify(timeline).save(any(ShipmentEvent.class));
        verify(outbox).write(eq("ShipmentStatusChangedEvent"), eq("shipment.status-changed"), eq("500"), any(ShipmentStatusFields.class));
    }

    @Test
    void cashOnDeliveryMustCollectTheOrderTotal() {
        when(shipments.existsByOrderId(ORDER)).thenReturn(false);
        when(orderClient.get(ORDER)).thenReturn(order("CONFIRMED", "250.00"));
        when(shipments.save(any(Shipment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createShipmentForOrder(ORDER, "COD");

        ArgumentCaptor<Shipment> cap = ArgumentCaptor.forClass(Shipment.class);
        verify(shipments).save(cap.capture());
        assertThat(cap.getValue().getCashToCollect()).isEqualByComparingTo("250.00");
        assertThat(cap.getValue().getPaymentMethod()).isEqualTo("COD");
    }

    @Test
    void duplicateCreationIsAnIdempotentNoOp() {
        when(shipments.existsByOrderId(ORDER)).thenReturn(true);

        service.createShipmentForOrder(ORDER, "STRIPE");

        verify(orderClient, never()).get(any());
        verify(shipments, never()).save(any());
    }

    @Test
    void cancelledOrderGetsNoShipment() {
        when(shipments.existsByOrderId(ORDER)).thenReturn(false);
        when(orderClient.get(ORDER)).thenReturn(order("CANCELLED", "10"));

        service.createShipmentForOrder(ORDER, "STRIPE");

        verify(shipments, never()).save(any());
        verify(outbox, never()).write(anyString(), anyString(), anyString(), any());
    }

    // ------------------------------------------------------------ seller pack

    @Test
    void sellerPacksTheParcel() {
        Shipment s = found(ShipmentStatus.AWAITING_SELLER);
        when(timeline.findByOrderIdOrderByCreatedAtAscIdAsc(ORDER)).thenReturn(List.of());

        ShipmentView v = service.markReadyToShip(ORDER, user(SELLER, "SELLER"));

        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.READY_TO_SHIP);
        assertThat(v.status()).isEqualTo("READY_TO_SHIP");
        verify(shipments).saveAndFlush(s);
        verify(outbox).write(eq("ShipmentStatusChangedEvent"), anyString(), anyString(), any(ShipmentStatusFields.class));
    }

    @Test
    void adminMayPackToo() {
        found(ShipmentStatus.AWAITING_SELLER);
        when(timeline.findByOrderIdOrderByCreatedAtAscIdAsc(ORDER)).thenReturn(List.of());
        assertThat(service.markReadyToShip(ORDER, user(1L, "ADMIN")).status()).isEqualTo("READY_TO_SHIP");
    }

    @Test
    void anotherSellerCannotPack() {
        found(ShipmentStatus.AWAITING_SELLER);
        assertThatThrownBy(() -> service.markReadyToShip(ORDER, user(1234L, "SELLER")))
                .isInstanceOf(ForbiddenOperationException.class);
        verify(shipments, never()).saveAndFlush(any());
    }

    @Test
    void packingAnAlreadyPackedParcelIsAConflict() {
        found(ShipmentStatus.READY_TO_SHIP);
        assertThatThrownBy(() -> service.markReadyToShip(ORDER, user(SELLER, "SELLER")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("READY_TO_SHIP");
    }

    @Test
    void missingShipmentIsNotFound() {
        when(shipments.findByOrderId(ORDER)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markReadyToShip(ORDER, user(SELLER, "SELLER")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void concurrentChangeIsReportedAsFriendlyConflict() {
        Shipment s = found(ShipmentStatus.AWAITING_SELLER);
        when(shipments.saveAndFlush(s)).thenThrow(new ObjectOptimisticLockingFailureException(Shipment.class, 1L));

        assertThatThrownBy(() -> service.markReadyToShip(ORDER, user(SELLER, "SELLER")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("refresh");
        verify(timeline, never()).save(any());
    }

    // ------------------------------------------------------------ cancel

    @Test
    void sellerMustGiveAReasonToCancel() {
        found(ShipmentStatus.AWAITING_SELLER);
        assertThatThrownBy(() -> service.cancel(ORDER, "  ", user(SELLER, "SELLER")))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void sellerCancelsWithReason() {
        Shipment s = found(ShipmentStatus.READY_TO_SHIP);
        when(timeline.findByOrderIdOrderByCreatedAtAscIdAsc(ORDER)).thenReturn(List.of());

        service.cancel(ORDER, "Out of stock", user(SELLER, "SELLER"));

        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        ArgumentCaptor<ShipmentEvent> ev = ArgumentCaptor.forClass(ShipmentEvent.class);
        verify(timeline).save(ev.capture());
        assertThat(ev.getValue().getNote()).isEqualTo("Cancelled by the seller: Out of stock");
    }

    @Test
    void customerCanCancelTheirOwnParcelWithoutAReason() {
        Shipment s = found(ShipmentStatus.AWAITING_SELLER);
        when(timeline.findByOrderIdOrderByCreatedAtAscIdAsc(ORDER)).thenReturn(List.of());

        service.cancel(ORDER, null, user(CUSTOMER, "CUSTOMER"));

        assertThat(s.getStatus()).isEqualTo(ShipmentStatus.CANCELLED);
        ArgumentCaptor<ShipmentEvent> ev = ArgumentCaptor.forClass(ShipmentEvent.class);
        verify(timeline).save(ev.capture());
        assertThat(ev.getValue().getNote()).isEqualTo("Cancelled by the customer.");
    }

    @Test
    void strangerCannotCancel() {
        found(ShipmentStatus.AWAITING_SELLER);
        assertThatThrownBy(() -> service.cancel(ORDER, "x", user(31337L, "CUSTOMER")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void parcelOnTheRoadCannotBeCancelled() {
        found(ShipmentStatus.OUT_FOR_DELIVERY);
        assertThatThrownBy(() -> service.cancel(ORDER, "x", user(SELLER, "SELLER")))
                .isInstanceOf(ConflictException.class);
    }

    // ------------------------------------------------------------ reads and the delivery code

    @Test
    void customerSeesDeliveryCodeOnlyWhileOutForDelivery() {
        found(ShipmentStatus.OUT_FOR_DELIVERY);
        when(timeline.findByOrderIdOrderByCreatedAtAscIdAsc(ORDER)).thenReturn(List.of());
        when(otp.codeFor(any(Shipment.class))).thenReturn("123456");

        ShipmentView v = service.getForOrder(ORDER, user(CUSTOMER, "CUSTOMER"));

        assertThat(v.deliveryCode()).isEqualTo("123456");
    }

    @Test
    void sellerNeverSeesTheDeliveryCode() {
        found(ShipmentStatus.OUT_FOR_DELIVERY);
        when(timeline.findByOrderIdOrderByCreatedAtAscIdAsc(ORDER)).thenReturn(List.of());

        ShipmentView v = service.getForOrder(ORDER, user(SELLER, "SELLER"));

        assertThat(v.deliveryCode()).isNull();
        verify(otp, never()).codeFor(any());
    }

    @Test
    void customerHasNoCodeBeforeTheParcelIsOnItsWay() {
        found(ShipmentStatus.READY_TO_SHIP);
        when(timeline.findByOrderIdOrderByCreatedAtAscIdAsc(ORDER)).thenReturn(List.of());
        assertThat(service.getForOrder(ORDER, user(CUSTOMER, "CUSTOMER")).deliveryCode()).isNull();
    }

    @Test
    void sellerSeesPackButtonOnlyWhileWaitingForThem() {
        found(ShipmentStatus.AWAITING_SELLER);
        when(timeline.findByOrderIdOrderByCreatedAtAscIdAsc(ORDER)).thenReturn(List.of());
        assertThat(service.getForOrder(ORDER, user(SELLER, "SELLER")).sellerCanPack()).isTrue();
        assertThat(service.getForOrder(ORDER, user(CUSTOMER, "CUSTOMER")).sellerCanPack()).isFalse();
    }

    @Test
    void assignedAgentMayReadButStrangersMayNot() {
        Shipment s = found(ShipmentStatus.OUT_FOR_DELIVERY);
        s.setAgentId(AGENT);
        when(timeline.findByOrderIdOrderByCreatedAtAscIdAsc(ORDER)).thenReturn(List.of());

        assertThat(service.getForOrder(ORDER, user(AGENT, "DELIVERY_PARTNER"))).isNotNull();
        assertThatThrownBy(() -> service.getForOrder(ORDER, user(8888L, "CUSTOMER")))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    void sellerListSkipsTheTimeline() {
        when(shipments.findBySellerIdOrderByCreatedAtDesc(SELLER)).thenReturn(List.of(shipment(ShipmentStatus.AWAITING_SELLER)));

        List<ShipmentView> list = service.forSeller(user(SELLER, "SELLER"));

        assertThat(list).hasSize(1);
        assertThat(list.get(0).timeline()).isEmpty();
        verify(timeline, never()).findByOrderIdOrderByCreatedAtAscIdAsc(any());
    }
}
