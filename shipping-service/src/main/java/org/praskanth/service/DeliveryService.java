package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.ShipmentDtos.PoolItem;
import org.praskanth.dto.ShipmentDtos.ShipmentView;
import org.praskanth.entity.Shipment;
import org.praskanth.entity.ShipmentStatus;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.repository.ShipmentRepository;
import org.praskanth.security.gateway.GatewayUser;
import org.praskanth.util.Pincodes;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Everything a delivery agent (role DELIVERY_PARTNER) can do:
 *   pool()    parcels packed by sellers and waiting, area only
 *   claim()   take one. Race-safe: two agents tapping "Take" at once -> one wins, the other gets 409
 *             (status check + optimistic lock on the shipment row, see ShippingService.move)
 *   deliver() hand over: needs the customer's delivery code (and the cash for COD)
 *   fail()    could not deliver: back to the pool for another try, or returned to the seller after too many failures
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryService {

    private static final int MAX_WRONG_CODES = 5;

    private final ShipmentRepository shipments;
    private final ShippingService shipping;
    private final OtpService otp;
    private final AgentDirectory directory;

    /** Parcels an agent may carry at once. */
    @Value("${shipping.agent.max-active:10}")
    private int maxActive;

    /** Delivery attempts before the parcel goes back to the seller (and the customer is refunded). */
    @Value("${shipping.delivery.max-attempts:2}")
    private int maxAttempts;

    @Transactional(readOnly = true)
    public List<PoolItem> pool(GatewayUser caller) {
        requireAgent(caller);
        AgentDirectory.AgentInfo me = directory.requireApproved();
        if (!me.onDuty()) {
            return List.of(); // off duty: nothing is offered
        }
        // only parcels going to the pincodes this agent serves
        return shipments.findTop50ByStatusAndShippingPincodeInOrderByUpdatedAtAsc(ShipmentStatus.READY_TO_SHIP, me.pincodes()).stream()
                .map(s -> new PoolItem(s.getOrderId(), s.getTrackingNumber(), areaOf(s.getShippingAddress()),
                        s.getPaymentMethod(), s.getCashToCollect(), s.getFailedAttempts(), s.getUpdatedAt()))
                .toList();
    }

    @Transactional
    public ShipmentView claim(Long orderId, GatewayUser caller) {
        requireAgent(caller);
        AgentDirectory.AgentInfo me = directory.requireOnDuty(); // approved AND on duty, checked now, not from a cache
        Shipment s = find(orderId);
        String pin = s.getShippingPincode() != null ? s.getShippingPincode() : Pincodes.extract(s.getShippingAddress());
        if (pin == null || !me.pincodes().contains(pin)) {
            throw new ForbiddenOperationException("This parcel (" + (pin == null ? "unknown pincode" : pin) + ") is outside your service area.");
        }
        if (s.getStatus() != ShipmentStatus.READY_TO_SHIP) {
            throw new ConflictException("This parcel is no longer available (it is " + s.getStatus() + ").");
        }
        if (shipments.countByAgentIdAndStatus(caller.id(), ShipmentStatus.OUT_FOR_DELIVERY) >= maxActive) {
            throw new ConflictException("You already carry " + maxActive + " parcels. Deliver some first.");
        }
        s.setAgentId(caller.id());
        s.setPickedUpAt(Instant.now());
        s.setOtpVersion(s.getOtpVersion() + 1); // fresh delivery code for this attempt
        s.setOtpAttempts(0);
        shipping.move(s, ShipmentStatus.OUT_FOR_DELIVERY, "AGENT #" + caller.id(),
                "Picked up by delivery agent #" + caller.id() + ". Share your delivery code only when you receive the parcel.");
        return shipping.view(s, caller);
    }

    /** active = parcels in my hands; otherwise my last 30 delivered. */
    @Transactional(readOnly = true)
    public List<ShipmentView> mine(boolean active, GatewayUser caller) {
        requireAgent(caller);
        List<Shipment> list = active
                ? shipments.findByAgentIdAndStatusOrderByPickedUpAtDesc(caller.id(), ShipmentStatus.OUT_FOR_DELIVERY)
                : shipments.findTop30ByAgentIdAndStatusOrderByDeliveredAtDesc(caller.id(), ShipmentStatus.DELIVERED);
        return list.stream().map(s -> shipping.view(s, caller, false)).toList();
    }

    /** noRollbackFor: a wrong code must still be COUNTED (otherwise the attempt limit would never trigger). */
    @Transactional(noRollbackFor = InvalidOtpException.class)
    public ShipmentView deliver(Long orderId, String code, Boolean cashCollected, GatewayUser caller) {
        Shipment s = ownParcel(orderId, caller);
        boolean cod = "COD".equals(s.getPaymentMethod());
        if (cod && !Boolean.TRUE.equals(cashCollected)) {
            throw new BadRequestException("Collect " + s.getCashToCollect() + " in cash first, then confirm it.");
        }
        if (s.getOtpAttempts() >= MAX_WRONG_CODES) {
            throw new ConflictException("Too many wrong codes. Mark the delivery as failed.");
        }
        if (!otp.matches(s, code)) {
            s.setOtpAttempts(s.getOtpAttempts() + 1);
            shipments.save(s);
            int left = MAX_WRONG_CODES - s.getOtpAttempts();
            throw new InvalidOtpException("Wrong delivery code. " + left + (left == 1 ? " attempt" : " attempts") + " left.");
        }
        s.setDeliveredAt(Instant.now());
        s.setCashCollected(cod ? Boolean.TRUE : null);
        shipping.move(s, ShipmentStatus.DELIVERED, "AGENT #" + caller.id(),
                cod ? "Delivered. Cash " + s.getCashToCollect() + " collected." : "Delivered. Code confirmed.");
        return shipping.view(s, caller);
    }

    @Transactional
    public ShipmentView fail(Long orderId, String reason, GatewayUser caller) {
        if (reason == null || reason.isBlank()) {
            throw new BadRequestException("Please say why the delivery failed.");
        }
        Shipment s = ownParcel(orderId, caller);
        int attempt = s.getFailedAttempts() + 1;
        s.setFailedAttempts(attempt);
        s.setAgentId(null);
        s.setOtpAttempts(0);
        shipping.move(s, ShipmentStatus.DELIVERY_FAILED, "AGENT #" + caller.id(),
                "Delivery attempt " + attempt + " failed: " + reason.trim());
        if (attempt < maxAttempts) {
            shipping.move(s, ShipmentStatus.READY_TO_SHIP, "SYSTEM", "Back in the pool for another delivery attempt.");
        } else {
            shipping.move(s, ShipmentStatus.CANCELLED, "SYSTEM",
                    "Delivery failed " + attempt + " times. The parcel goes back to the seller. Online payments are refunded.");
        }
        return shipping.view(s, caller, true);
    }

    // ------------------------------------------------------------------ helpers

    private Shipment ownParcel(Long orderId, GatewayUser caller) {
        requireAgent(caller);
        Shipment s = find(orderId);
        if (s.getAgentId() == null || !s.getAgentId().equals(caller.id())) {
            throw new ForbiddenOperationException("You do not carry this parcel.");
        }
        if (s.getStatus() != ShipmentStatus.OUT_FOR_DELIVERY) {
            throw new ConflictException("This parcel is " + s.getStatus() + ", not out for delivery.");
        }
        return s;
    }

    private Shipment find(Long orderId) {
        return shipments.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("This order has not shipped yet."));
    }

    private void requireAgent(GatewayUser caller) {
        if (!caller.hasRole("DELIVERY_PARTNER")) {
            throw new ForbiddenOperationException("Delivery partners only.");
        }
    }

    /** "Name, street, city, state - pin, country" -> "city, state - pin, country" (the last three parts). */
    static String areaOf(String address) {
        if (address == null || address.isBlank()) {
            return "";
        }
        String[] parts = address.split(",\\s*");
        int from = Math.max(0, parts.length - 3);
        return String.join(", ", Arrays.copyOfRange(parts, from, parts.length));
    }
}
