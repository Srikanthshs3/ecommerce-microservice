package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.OrderInfo;
import org.praskanth.dto.ShipmentDtos.ShipmentView;
import org.praskanth.dto.ShipmentDtos.TimelineEntry;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Owns the parcel. A shipment is created when the customer's payment arrangement is settled (paid, or cash on
 * delivery) and then moves only along ShipmentStatus's allowed transitions. Every move writes a timeline line and a
 * ShipmentStatusChangedEvent (outbox) in the same transaction: order-service mirrors it, inventory-service finalises the
 * stock, and on CANCELLED order-service cancels the order, which triggers the refund.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ShippingService {

    private static final String CARRIER = "eShop Logistics";
    private static final String TOPIC = "shipment.status-changed";

    private final ShipmentRepository shipments;
    private final ShipmentEventRepository timeline;
    private final OrderClient orderClient;
    private final OutboxEventWriter outboxEventWriter;
    private final SecureRandom random = new SecureRandom();
    private final OtpService otp;

    // ------------------------------------------------------------------ creation (Kafka)

    /** Idempotent: unique order_id plus the Redis dedup in the consumer. */
    @Transactional
    public void createShipmentForOrder(Long orderId, String paymentMethod) {
        if (shipments.existsByOrderId(orderId)) {
            log.info("Shipment already recorded for order {}, skipping (idempotent no-op)", orderId);
            return;
        }
        OrderInfo order = orderClient.get(orderId);
        if ("CANCELLED".equals(order.getStatus())) {
            log.info("Order {} was cancelled before its shipment was created, nothing to ship", orderId);
            return;
        }
        boolean cod = "COD".equals(paymentMethod);
        Shipment shipment = shipments.save(Shipment.builder()
                .orderId(orderId)
                .trackingNumber("TRK" + (100000000 + random.nextInt(900000000)))
                .carrier(CARRIER)
                .status(ShipmentStatus.AWAITING_SELLER)
                .userId(order.getUserId())
                .sellerId(order.getSellerId())
                .shippingAddress(order.getShippingAddress())
                .shippingPincode(org.praskanth.util.Pincodes.extract(order.getShippingAddress()))
                .paymentMethod(paymentMethod)
                .cashToCollect(cod ? order.getTotalAmount() : BigDecimal.ZERO)
                .updatedAt(Instant.now())
                .build());
        record(shipment, "SYSTEM", cod
                ? "Order placed (cash on delivery). Waiting for the seller to pack it."
                : "Payment received. Waiting for the seller to pack it.");
        log.info("Shipment {} created for order {} (tracking {})", shipment.getId(), orderId, shipment.getTrackingNumber());
    }

    // ------------------------------------------------------------------ seller / customer actions

    /** The seller has packed the parcel: delivery agents can now see and take it. */
    @Transactional
    public ShipmentView markReadyToShip(Long orderId, GatewayUser caller) {
        Shipment shipment = find(orderId);
        requireSeller(shipment, caller);
        move(shipment, ShipmentStatus.READY_TO_SHIP, actor("SELLER", caller), "Packed by the seller. Waiting for a delivery agent.");
        return view(shipment, caller);
    }

    /**
     * Cancel before the parcel leaves. The seller must give a reason; the customer may cancel their own paid order
     * (they get the money back through the OrderCancelledEvent -> payment-service refund). An admin may do either.
     */
    @Transactional
    public ShipmentView cancel(Long orderId, String reason, GatewayUser caller) {
        Shipment shipment = find(orderId);
        boolean seller = shipment.getSellerId() != null && shipment.getSellerId().equals(caller.id());
        boolean owner = shipment.getUserId() != null && shipment.getUserId().equals(caller.id());
        if (!seller && !owner && !caller.isAdmin()) {
            throw new ForbiddenOperationException("You cannot cancel this order.");
        }
        String note;
        String who;
        if (seller || (caller.isAdmin() && !owner)) {
            if (reason == null || reason.isBlank()) {
                throw new BadRequestException("Please give a reason for cancelling.");
            }
            note = "Cancelled by the seller: " + reason.trim();
            who = actor(seller ? "SELLER" : "ADMIN", caller);
        } else {
            note = "Cancelled by the customer" + (reason == null || reason.isBlank() ? "." : ": " + reason.trim());
            who = actor("CUSTOMER", caller);
        }
        move(shipment, ShipmentStatus.CANCELLED, who, note);
        return view(shipment, caller);
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public ShipmentView getForOrder(Long orderId, GatewayUser caller) {
        Shipment shipment = find(orderId);
        boolean seller = shipment.getSellerId() != null && shipment.getSellerId().equals(caller.id());
        boolean owner = shipment.getUserId() != null && shipment.getUserId().equals(caller.id());
        boolean agent = shipment.getAgentId() != null && shipment.getAgentId().equals(caller.id());
        if (!seller && !owner && !caller.isAdmin() && !agent) {
            throw new ForbiddenOperationException("This shipment is not yours.");
        }
        return view(shipment, caller);
    }

    /** The seller's own shipments, newest first (the seller page joins them with the orders). */
    @Transactional(readOnly = true)
    public List<ShipmentView> forSeller(GatewayUser caller) {
        return shipments.findBySellerIdOrderByCreatedAtDesc(caller.id()).stream()
                .map(s -> view(s, caller, false)).toList();
    }

    // ------------------------------------------------------------------ the one place that changes a status

    /** Shared with Stage 3 (agent pickup / delivery). */
    Shipment move(Shipment shipment, ShipmentStatus next, String actor, String note) {
        if (!shipment.getStatus().canMoveTo(next)) {
            throw new ConflictException("The shipment is " + shipment.getStatus() + " and cannot move to " + next + ".");
        }
        shipment.setStatus(next);
        shipment.setUpdatedAt(Instant.now());
        try {
            shipments.saveAndFlush(shipment); // flush now so a concurrent change (optimistic lock) fails HERE, not later
        } catch (ObjectOptimisticLockingFailureException ex) {
            throw new ConflictException("The shipment was just changed by someone else. Please refresh and try again.");
        }
        record(shipment, actor, note);
        return shipment;
    }

    private void record(Shipment shipment, String actor, String note) {
        timeline.save(ShipmentEvent.builder()
                .shipmentId(shipment.getId()).orderId(shipment.getOrderId())
                .status(shipment.getStatus().name()).note(note).actor(actor).build());
        outboxEventWriter.write("ShipmentStatusChangedEvent", TOPIC, String.valueOf(shipment.getOrderId()),
                new ShipmentStatusFields(shipment.getOrderId(), shipment.getId(), shipment.getStatus().name(), note, actor, Instant.now()));
    }

    // ------------------------------------------------------------------ helpers

    private Shipment find(Long orderId) {
        return shipments.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("This order has not shipped yet."));
    }

    private void requireSeller(Shipment shipment, GatewayUser caller) {
        boolean seller = shipment.getSellerId() != null && shipment.getSellerId().equals(caller.id());
        if (!seller && !caller.isAdmin()) {
            throw new ForbiddenOperationException("Only the seller of this order can do that.");
        }
    }

    private static String actor(String role, GatewayUser caller) {
        return role + " #" + caller.id();
    }

    ShipmentView view(Shipment s, GatewayUser caller) {
        return view(s, caller, true);
    }

    ShipmentView view(Shipment s, GatewayUser caller, boolean withTimeline) {
        List<TimelineEntry> entries = withTimeline
                ? timeline.findByOrderIdOrderByCreatedAtAscIdAsc(s.getOrderId()).stream()
                        .map(e -> new TimelineEntry(e.getStatus(), e.getNote(), e.getActor(), e.getCreatedAt())).toList()
                : List.of();
        boolean sellerSide = s.getSellerId() != null && s.getSellerId().equals(caller.id());
        boolean customerSide = s.getUserId() != null && s.getUserId().equals(caller.id());
        // The delivery code is the customer's secret: only they see it, only while the parcel is on its way to them.
        String code = customerSide && s.getStatus() == ShipmentStatus.OUT_FOR_DELIVERY ? otp.codeFor(s) : null;
        // Cash to collect and the address are for the people who handle the parcel, not a public detail
        return new ShipmentView(s.getOrderId(), s.getTrackingNumber(), s.getCarrier(), s.getStatus().name(),
                s.getPaymentMethod(), s.getCashToCollect(), s.getShippingAddress(),
                sellerSide && s.getStatus() == ShipmentStatus.AWAITING_SELLER,
                s.getStatus().isCancellable(), s.getCreatedAt(), s.getUpdatedAt(), entries,
                code, s.getAgentId(), s.getFailedAttempts(), s.getCashCollected());
    }
}
