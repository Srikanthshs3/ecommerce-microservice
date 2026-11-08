package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.entity.*;
import org.praskanth.exception.PaymentGatewayException;
import org.praskanth.gateway.GatewayCaller;
import org.praskanth.gateway.PaymentGatewayStrategy;
import org.praskanth.gateway.WebhookEvent;
import org.praskanth.outbox.OutboxEventWriter;
import org.praskanth.outbox.PaymentCompletedFields;
import org.praskanth.repository.CheckoutPaymentRepository;
import org.praskanth.repository.PaymentAllocationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Every state change that settles money lives here, so the three entry points (browser confirm, gateway webhook,
 * order-cancelled event) share ONE implementation. Each method expects the payment row to be locked by the caller
 * (or locks it itself) and is safe to run twice: a second run sees the new status and does nothing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentSettlementService {

    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";

    private final CheckoutPaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final OutboxEventWriter outboxEventWriter;
    private final Map<PaymentGateway, PaymentGatewayStrategy> strategies;
    private final GatewayCaller gateway;

    /** The gateway says this checkout was paid. Caller holds the row lock. */
    public void succeed(CheckoutPayment payment, String gatewayPaymentId) {
        switch (payment.getStatus()) {
            case SUCCEEDED, PARTIALLY_REFUNDED, REFUNDED, PENDING_COLLECTION -> log.info(
                    "Checkout {} already {}, ignoring duplicate success", payment.getCheckoutRef(), payment.getStatus());
            case CANCELLED -> {
                // Paid at the gateway AFTER every order was cancelled (expired while the customer was typing the card):
                // never keep money for an order that no longer exists -> give it all back.
                payment.setGatewayPaymentId(gatewayPaymentId);
                log.warn("Checkout {} was paid after it was cancelled; refunding everything", payment.getCheckoutRef());
                for (PaymentAllocation a : payment.getAllocations()) {
                    refundAllocation(payment, a);
                }
            }
            case CREATED -> {
                payment.setStatus(CheckoutPaymentStatus.SUCCEEDED);
                payment.setGatewayPaymentId(gatewayPaymentId);
                payment.setFailureReason(null);
                payment.setCompletedAt(Instant.now());
                payments.save(payment);
                publishCompleted(payment);
                log.info("Checkout {} paid via {} ({})", payment.getCheckoutRef(), payment.getMethod(), gatewayPaymentId);
            }
        }
    }

    /** Cash on delivery: nothing to verify, the order is placed and the money is collected at the door. */
    public void placeCashOnDelivery(CheckoutPayment payment) {
        payment.setStatus(CheckoutPaymentStatus.PENDING_COLLECTION);
        payment.setCompletedAt(Instant.now());
        payments.save(payment);
        publishCompleted(payment);
    }

    /** One PaymentCompletedEvent per order (order-service confirms it, shipping-service prepares the parcel). */
    private void publishCompleted(CheckoutPayment payment) {
        String txn = payment.getMethod() == PaymentMethod.COD ? "COD-" + payment.getCheckoutRef() : payment.getGatewayPaymentId();
        for (PaymentAllocation a : payment.getAllocations()) {
            if (a.getStatus() != AllocationStatus.ACTIVE) {
                continue;
            }
            outboxEventWriter.write(
                    "PaymentCompletedEvent",
                    PAYMENT_COMPLETED_TOPIC,
                    String.valueOf(a.getOrderId()),
                    new PaymentCompletedFields(a.getOrderId(), a.getAmount(), txn, payment.getMethod().name(), Instant.now()));
        }
    }

    /**
     * Reaction to ShipmentStatusChangedEvent=DELIVERED. Online payments are already SUCCEEDED, nothing to do. For cash on
     * delivery the agent confirmed the money at the door, so that order's share is COLLECTED; the whole checkout
     * becomes COLLECTED once every (non-cancelled) share is.
     */
    @Transactional
    public void onDelivered(Long orderId) {
        Optional<PaymentAllocation> found = allocations.findByOrderId(orderId);
        if (found.isEmpty()) {
            return;
        }
        CheckoutPayment payment = payments.lockById(found.get().getPayment().getId()).orElseThrow();
        if (payment.getStatus() != CheckoutPaymentStatus.PENDING_COLLECTION) {
            return; // paid online (or already settled): the delivery changes no money
        }
        payment.getAllocations().stream()
                .filter(a -> a.getOrderId().equals(orderId) && a.getStatus() == AllocationStatus.ACTIVE)
                .forEach(a -> a.setStatus(AllocationStatus.COLLECTED));
        boolean done = payment.getAllocations().stream().noneMatch(a -> a.getStatus() == AllocationStatus.ACTIVE);
        if (done) {
            payment.setStatus(CheckoutPaymentStatus.COLLECTED);
        }
        payments.save(payment);
        log.info("Order {}: cash collected (checkout {} is {})", orderId, payment.getCheckoutRef(), payment.getStatus());
    }

    /** Reaction to OrderCancelledEvent: void what is unpaid, refund what is paid. */
    @Transactional
    public void onOrderCancelled(Long orderId, String reason) {
        Optional<PaymentAllocation> found = allocations.findByOrderId(orderId);
        if (found.isEmpty()) {
            log.info("Order {} has no payment (cancelled before payment started)", orderId);
            return;
        }
        CheckoutPayment payment = payments.lockById(found.get().getPayment().getId()).orElseThrow();
        PaymentAllocation allocation = payment.getAllocations().stream()
                .filter(a -> a.getOrderId().equals(orderId)).findFirst().orElseThrow();

        switch (payment.getStatus()) {
            case CREATED, PENDING_COLLECTION -> {
                if (allocation.getStatus() == AllocationStatus.ACTIVE) {
                    allocation.setStatus(AllocationStatus.CANCELLED);
                }
                boolean allCancelled = payment.getAllocations().stream().allMatch(a -> a.getStatus() == AllocationStatus.CANCELLED);
                if (allCancelled) {
                    payment.setStatus(CheckoutPaymentStatus.CANCELLED);
                    payment.setFailureReason(reason);
                    if (payment.getMethod().isOnline() && payment.getGatewayOrderId() != null) {
                        gateway.cancelOrderQuietly(strategies.get(payment.getMethod().gateway()), payment.getGatewayOrderId());
                    }
                }
                payments.save(payment);
            }
            case SUCCEEDED, PARTIALLY_REFUNDED -> {
                refundAllocation(payment, allocation);
                payments.save(payment);
            }
            default -> log.info("Order {} cancelled; checkout {} is {}, nothing to do", orderId, payment.getCheckoutRef(), payment.getStatus());
        }
    }

    /** Gateway webhook: the safety net for "customer paid but closed the browser before we heard about it". */
    @Transactional
    public void onGatewayEvent(WebhookEvent event) {
        if (event.type() == WebhookEvent.Type.IGNORED || event.gatewayOrderId() == null) {
            return;
        }
        Optional<CheckoutPayment> found = payments.lockByGatewayOrderId(event.gatewayOrderId());
        if (found.isEmpty()) {
            log.info("Webhook for unknown gateway order {}", event.gatewayOrderId());
            return;
        }
        CheckoutPayment payment = found.get();
        if (event.type() == WebhookEvent.Type.SUCCEEDED) {
            succeed(payment, event.gatewayPaymentId());
        } else if (payment.getStatus() == CheckoutPaymentStatus.CREATED) {
            payment.setAttempts(payment.getAttempts() + 1);
            payment.setFailureReason(truncate(event.reason()));
            payments.save(payment);
        }
    }

    private void refundAllocation(CheckoutPayment payment, PaymentAllocation allocation) {
        BigDecimal remaining = allocation.getAmount().subtract(allocation.getRefundedAmount());
        if (remaining.signum() <= 0 || !payment.getMethod().isOnline() || payment.getGatewayPaymentId() == null) {
            return;
        }
        PaymentGatewayStrategy strategy = strategies.get(payment.getMethod().gateway());
        try {
            // Deterministic idempotency key: a retry after a half-finished attempt cannot refund twice.
            String refundId = gateway.refund(strategy, payment.getGatewayPaymentId(), remaining, "refund-order-" + allocation.getOrderId());
            allocation.setRefundId(refundId);
        } catch (Exception ex) {
            throw new PaymentGatewayException("The refund could not be processed right now. Please try again shortly.", ex);
        }
        allocation.setRefundedAmount(allocation.getAmount());
        allocation.setStatus(AllocationStatus.REFUNDED);
        payment.setRefundedAmount(payment.getRefundedAmount().add(remaining));
        boolean all = payment.getAllocations().stream().allMatch(a -> a.getStatus() == AllocationStatus.REFUNDED);
        payment.setStatus(all ? CheckoutPaymentStatus.REFUNDED : CheckoutPaymentStatus.PARTIALLY_REFUNDED);
    }

    static String truncate(String s) {
        return s == null ? null : (s.length() > 250 ? s.substring(0, 250) : s);
    }
}
