package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.dto.CheckoutOrder;
import org.praskanth.dto.PaymentDtos.*;
import org.praskanth.entity.*;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.exception.PaymentGatewayException;
import org.praskanth.exception.PaymentVerificationException;
import org.praskanth.gateway.GatewayCaller;
import org.praskanth.gateway.PaymentGatewayStrategy;
import org.praskanth.gateway.VerifiedPayment;
import org.praskanth.repository.CheckoutPaymentRepository;
import org.praskanth.repository.PaymentAllocationRepository;
import org.praskanth.security.gateway.GatewayUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Checkout payment, Amazon style: the customer places the order(s) first (stock is reserved), then pays ONCE for the
 * whole checkout by Razorpay, Stripe or cash on delivery. Once paid, one PaymentCompletedEvent per order lets
 * order-service confirm each order. Nothing the browser says is trusted: online payments are verified with the gateway.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final CheckoutPaymentRepository payments;
    private final PaymentAllocationRepository allocations;
    private final OrderClient orderClient;
    private final Map<PaymentGateway, PaymentGatewayStrategy> strategies;
    private final PaymentSettlementService settlement;
    private final GatewayCaller gateway; // every network call to Razorpay / Stripe goes through here (see GatewayCaller)

    @Value("${payment.currency:INR}")
    private String currency;

    @Value("${payment.cod.enabled:true}")
    private boolean codEnabled;

    /** Cash on delivery is limited for high-value orders (less risk for the seller and the delivery agent). */
    @Value("${payment.cod.max-amount:50000}")
    private BigDecimal codMaxAmount;

    // ------------------------------------------------------------------ methods

    public List<MethodInfo> methods() {
        List<MethodInfo> list = new ArrayList<>();
        PaymentGatewayStrategy razorpay = strategies.get(PaymentGateway.RAZORPAY);
        PaymentGatewayStrategy stripe = strategies.get(PaymentGateway.STRIPE);
        list.add(new MethodInfo("RAZORPAY", "Razorpay - cards, UPI, netbanking (test mode)",
                razorpay != null && razorpay.isAvailable(), "Test card 4111 1111 1111 1111, UPI success@razorpay"));
        list.add(new MethodInfo("STRIPE", "Stripe - card (test mode)",
                stripe != null && stripe.isAvailable(), "Test card 4242 4242 4242 4242, any future date, any CVC"));
        list.add(new MethodInfo("COD", "Cash on delivery", codEnabled, "Pay when the order arrives. Up to " + codMaxAmount));
        return list;
    }

    // ------------------------------------------------------------------ summary

    @Transactional(readOnly = true)
    public CheckoutSummary summary(String checkoutRef, GatewayUser caller) {
        List<CheckoutOrder> orders = loadOwnedOrders(checkoutRef, caller);
        return buildSummary(checkoutRef, orders, payments.findByCheckoutRef(checkoutRef).orElse(null));
    }

    // ------------------------------------------------------------------ initiate

    @Transactional
    public InitiateResponse initiate(String checkoutRef, PaymentMethod method, GatewayUser caller) {
        if (method == null) {
            throw new BadRequestException("Choose a payment method.");
        }
        List<CheckoutOrder> orders = loadOwnedOrders(checkoutRef, caller);
        CheckoutPayment payment = payments.lockByCheckoutRef(checkoutRef).orElse(null);

        // Already settled: answer idempotently (double click, page reload, retry after a timeout).
        if (payment != null && isSettled(payment.getStatus())) {
            return new InitiateResponse(checkoutRef, payment.getMethod().name(), payment.getStatus().name(),
                    payment.getAmount(), payment.getCurrency(), null);
        }

        requirePayable(orders);
        BigDecimal amount = orders.stream().map(CheckoutOrder::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        if (payment == null) {
            payment = CheckoutPayment.builder()
                    .checkoutRef(checkoutRef).userId(caller.id()).method(method)
                    .amount(amount).currency(currency).status(CheckoutPaymentStatus.CREATED).build();
            for (CheckoutOrder order : orders) {
                payment.getAllocations().add(PaymentAllocation.builder()
                        .payment(payment).orderId(order.getId()).amount(order.getTotalAmount()).build());
            }
        } else if (payment.getStatus() == CheckoutPaymentStatus.CANCELLED) {
            throw new ConflictException("This checkout was cancelled. Place the order again.");
        }

        if (method == PaymentMethod.COD) {
            if (!codEnabled) {
                throw new BadRequestException("Cash on delivery is not available.");
            }
            if (amount.compareTo(codMaxAmount) > 0) {
                throw new BadRequestException("Cash on delivery is limited to " + codMaxAmount + ". Pay online instead.");
            }
            retire(payment, method);
            payment.setMethod(PaymentMethod.COD);
            payment.setGatewayOrderId(null);
            settlement.placeCashOnDelivery(payment);
            return new InitiateResponse(checkoutRef, "COD", payment.getStatus().name(), amount, currency, null);
        }

        PaymentGatewayStrategy strategy = strategies.get(method.gateway());
        if (strategy == null || !strategy.isAvailable()) {
            throw new BadRequestException(method + " is not available right now. Pick another payment method.");
        }
        try {
            boolean reuse = payment.getGatewayOrderId() != null && payment.getMethod() == method
                    && payment.getAmount().compareTo(amount) == 0;
            if (!reuse) {
                retire(payment, method); // switching method: close the old gateway order
                payment.setMethod(method);
                payment.setAmount(amount);
                payment.setGatewayOrderId(gateway.createOrder(strategy, checkoutRef, amount, currency));
            }
            payments.save(payment);
            Map<String, Object> client = strategy.clientConfig(payment.getGatewayOrderId(), amount, currency);
            return new InitiateResponse(checkoutRef, method.name(), payment.getStatus().name(), amount, currency, client);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PaymentGatewayException("We couldn't start the online payment right now. Please try again in a moment or choose another payment method.", ex);
        }
    }

    /** Closes the gateway order of an earlier, different method (best effort). */
    private void retire(CheckoutPayment payment, PaymentMethod newMethod) {
        if (payment.getGatewayOrderId() != null && payment.getMethod() != newMethod && payment.getMethod().isOnline()) {
            gateway.cancelOrderQuietly(strategies.get(payment.getMethod().gateway()), payment.getGatewayOrderId());
        }
    }

    // ------------------------------------------------------------------ confirm

    /** The browser says "I paid". We do not believe it: the gateway is asked, then the checkout is settled. */
    @Transactional
    public CheckoutSummary confirm(String checkoutRef, ConfirmRequest request, GatewayUser caller) {
        List<CheckoutOrder> orders = loadOwnedOrders(checkoutRef, caller);
        CheckoutPayment payment = payments.lockByCheckoutRef(checkoutRef)
                .orElseThrow(() -> new NotFoundException("No payment has been started for this checkout yet."));
        if (!payment.getUserId().equals(caller.id())) {
            throw new ForbiddenOperationException("This payment belongs to another customer.");
        }

        if ((payment.getStatus() == CheckoutPaymentStatus.CREATED || payment.getStatus() == CheckoutPaymentStatus.CANCELLED)
                && payment.getMethod().isOnline()) {
            PaymentGatewayStrategy strategy = strategies.get(payment.getMethod().gateway());
            Map<String, String> proof = new java.util.HashMap<>();
            proof.put("razorpayOrderId", request.razorpayOrderId());
            proof.put("razorpayPaymentId", request.razorpayPaymentId());
            proof.put("razorpaySignature", request.razorpaySignature());
            proof.put("paymentIntentId", request.paymentIntentId());
            VerifiedPayment verified;
            try {
                verified = gateway.verify(strategy, payment.getGatewayOrderId(), payment.getAmount(), proof);
            } catch (PaymentVerificationException ex) {
                throw ex;
            } catch (Exception ex) {
                throw new PaymentGatewayException("We couldn't confirm your payment with the payment provider yet. If money was deducted, it will be matched to your order automatically. Otherwise please try again.", ex);
            }
            settlement.succeed(payment, verified.gatewayPaymentId());
        }
        return buildSummary(checkoutRef, orders, payment);
    }

    /** The gateway form reported a failed try (declined card, closed popup). Informational: the customer may try again. */
    @Transactional
    public void reportFailure(String checkoutRef, String reason, GatewayUser caller) {
        CheckoutPayment payment = payments.lockByCheckoutRef(checkoutRef)
                .orElseThrow(() -> new NotFoundException("No payment has been started for this checkout yet."));
        if (!payment.getUserId().equals(caller.id())) {
            throw new ForbiddenOperationException("This payment belongs to another customer.");
        }
        if (payment.getStatus() == CheckoutPaymentStatus.CREATED) {
            payment.setAttempts(payment.getAttempts() + 1);
            payment.setFailureReason(PaymentSettlementService.truncate(reason));
            payments.save(payment);
        }
    }

    // ------------------------------------------------------------------ per order

    @Transactional(readOnly = true)
    public OrderPaymentView byOrder(Long orderId, GatewayUser caller) {
        PaymentAllocation allocation = allocations.findByOrderId(orderId)
                .orElseThrow(() -> new NotFoundException("No payment has been recorded for this order yet."));
        CheckoutPayment payment = allocation.getPayment();
        if (!payment.getUserId().equals(caller.id()) && !caller.isAdmin()) {
            throw new ForbiddenOperationException("This payment belongs to another customer.");
        }
        return new OrderPaymentView(orderId, payment.getCheckoutRef(), payment.getMethod().name(),
                payment.getStatus().name(), allocation.getAmount(), payment.getGatewayPaymentId());
    }

    // ------------------------------------------------------------------ helpers

    private List<CheckoutOrder> loadOwnedOrders(String checkoutRef, GatewayUser caller) {
        List<CheckoutOrder> orders = orderClient.findByCheckoutRef(checkoutRef);
        if (orders.isEmpty()) {
            throw new NotFoundException("We couldn't find that checkout. Please start again from your cart.");
        }
        for (CheckoutOrder order : orders) {
            if (!order.getUserId().equals(caller.id())) {
                throw new ForbiddenOperationException("This checkout belongs to another customer.");
            }
        }
        return orders;
    }

    /** Payment can start only while every order has its stock reserved and the payment window is open. */
    private void requirePayable(List<CheckoutOrder> orders) {
        for (CheckoutOrder order : orders) {
            switch (order.getStatus()) {
                case "AWAITING_PAYMENT" -> { }
                case "PENDING" -> throw new ConflictException("Your items are still being reserved. Please try again in a moment.");
                case "CANCELLED" -> throw new ConflictException("This checkout was cancelled or has expired. Please place the order again.");
                default -> throw new ConflictException("Order " + order.getId() + " is already " + order.getStatus() + ".");
            }
            if (order.getExpiresAt() != null && order.getExpiresAt().isBefore(Instant.now())) {
                throw new ConflictException("The payment time for this checkout is over. Please place the order again.");
            }
        }
    }

    private static boolean isSettled(CheckoutPaymentStatus status) {
        return status == CheckoutPaymentStatus.SUCCEEDED || status == CheckoutPaymentStatus.PENDING_COLLECTION
                || status == CheckoutPaymentStatus.COLLECTED
                || status == CheckoutPaymentStatus.PARTIALLY_REFUNDED || status == CheckoutPaymentStatus.REFUNDED;
    }

    private CheckoutSummary buildSummary(String checkoutRef, List<CheckoutOrder> orders, CheckoutPayment payment) {
        BigDecimal amount = orders.stream().map(CheckoutOrder::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Instant expiresAt = orders.stream().map(CheckoutOrder::getExpiresAt).filter(java.util.Objects::nonNull)
                .min(Instant::compareTo).orElse(null);
        List<OrderLine> lines = orders.stream()
                .map(o -> new OrderLine(o.getId(), o.getStatus(), o.getTotalAmount())).toList();

        String state;
        String message = null;
        Optional<CheckoutOrder> cancelled = orders.stream().filter(o -> "CANCELLED".equals(o.getStatus())).findFirst();
        if (payment != null && (payment.getStatus() == CheckoutPaymentStatus.SUCCEEDED
                || payment.getStatus() == CheckoutPaymentStatus.PARTIALLY_REFUNDED)) {
            state = "PAID";
        } else if (payment != null && payment.getStatus() == CheckoutPaymentStatus.PENDING_COLLECTION) {
            state = "COD_PLACED";
        } else if (payment != null && payment.getStatus() == CheckoutPaymentStatus.REFUNDED) {
            state = "REFUNDED";
            message = "The payment was refunded.";
        } else if (cancelled.isPresent()) {
            String reason = cancelled.get().getCancellationReason();
            state = reason != null && reason.contains("not completed in time") ? "EXPIRED" : "CANCELLED";
            message = reason;
        } else if (orders.stream().allMatch(o -> List.of("CONFIRMED", "SHIPPED", "DELIVERED").contains(o.getStatus()))) {
            state = "PAID";
        } else if (orders.stream().anyMatch(o -> "PENDING".equals(o.getStatus()))) {
            state = "RESERVING";
        } else {
            state = "READY_TO_PAY";
        }

        PaymentInfo info = payment == null ? null : new PaymentInfo(payment.getMethod().name(),
                payment.getStatus().name(), payment.getAttempts(), payment.getFailureReason());
        return new CheckoutSummary(checkoutRef, amount, currency, state, expiresAt, lines, info, message);
    }
}
