package org.praskanth.service;

import org.praskanth.config.CartApiClient;
import org.praskanth.config.CouponApiClient;
import org.praskanth.dto.*;
import org.praskanth.entity.Order;
import org.praskanth.entity.OrderItem;
import org.praskanth.entity.OrderStatus;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.ForbiddenOperationException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.outbox.OrderCancelledFields;
import org.praskanth.notification.OrderNotificationPublisher;
import org.praskanth.notification.OrderStage;
import org.praskanth.outbox.OrderCreatedFields;
import org.praskanth.outbox.OutboxEventWriter;
import org.praskanth.repository.OrderRepository;
import org.praskanth.security.gateway.GatewayUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Saga initiator (create() publishes OrderCreatedEvent) and "scoreboard" (the transition*
 * methods, called by Kafka consumers reacting to downstream outcome events, move status
 * through PENDING -> AWAITING_PAYMENT -> CONFIRMED -> SHIPPED, or -> CANCELLED on failure).
 *
 * Every transition method checks the order's CURRENT status before applying its change and
 * is a no-op if the order isn't in the expected preceding state. This matters because Kafka
 * only guarantees ordering within a partition, and a redelivered/duplicate event (the consumer's
 * own Redis dedup is the primary guard, this is defense in depth) or an unlucky reordering
 * should never move an already-CANCELLED order back to CONFIRMED, for example.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final String ORDER_CREATED_TOPIC = "order.created";
    private static final String ORDER_CANCELLED_TOPIC = "order.cancelled";
    private static final Set<String> PAYMENT_METHODS = Set.of("RAZORPAY", "STRIPE", "COD");

    private final OrderRepository orderRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final CartApiClient cartApiClient;
    private final CouponApiClient couponApiClient;
    private final OrderNotificationPublisher notificationPublisher;

    /** How long a customer has to pay; stock stays reserved this long. */
    @Value("${order.payment-window-minutes:15}")
    private long paymentWindowMinutes;

    @Transactional
    public List<OrderResponse> create(OrderRequest request, UserResponse user) {

        AddressResponse selectedAddress = shippingAddress(user, request.getAddressId());
        String formatedAddress = formatAddress(selectedAddress);

        CartResponse cart = cartApiClient.getUserCart().getData();
        if (cart==null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Your cart is empty. Add something to your cart before placing an order.");
        }

        Map<Long, List<CartItemResponse>> itemsBySeller = cart.getCartItems().stream()
                .collect(Collectors.groupingBy(CartItemResponse::getSellerId));

        // Coupon: used (re-validated, counted) BEFORE anything is saved, so a coupon that stopped being valid
        // fails the whole checkout cleanly. If the transaction later does not commit, the use is given back.
        String appliedCoupon = null;
        // One id for every order of this click (one per seller). The customer pays ONCE for the whole checkout, and the
        // coupon is redeemed once under the same id.
        String checkoutRef = UUID.randomUUID().toString();
        Instant expiresAt = Instant.now().plus(paymentWindowMinutes, ChronoUnit.MINUTES);
        Map<Long, BigDecimal> couponShares = Map.of();
        if (cart.getCouponCode() != null && !cart.getCouponCode().isBlank()) {
            appliedCoupon = cart.getCouponCode();
            couponShares = redeemCoupon(appliedCoupon, checkoutRef, itemsBySeller);
        }

        Set<Order> orders = new HashSet<>();

        for (Map.Entry<Long, List<CartItemResponse>> entry : itemsBySeller.entrySet()) {
            Long sellerId = entry.getKey();
            List<CartItemResponse> sellerCartItems = entry.getValue();

            BigDecimal sellerTotalMrp = sellerCartItems.stream()
                    .map(CartItemResponse::getTotalMrpPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal sellerTotalSelling = sellerCartItems.stream()
                    .map(CartItemResponse::getTotalSellingPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            int discountPercentage = calculateDiscount(sellerTotalMrp, sellerTotalSelling);
            BigDecimal couponShare = couponShares.getOrDefault(sellerId, BigDecimal.ZERO).min(sellerTotalSelling);

            Order order = Order.builder()
                    .userId(user.getId())
                    .customerEmail(user.getEmail())
                    .customerName(user.getName())
                    .sellerId(sellerId)
                    .status(OrderStatus.PENDING)
                    .shippingAddressId(selectedAddress.getId())
                    .shippingAddress(formatedAddress)
                    .totalMrpPrice(sellerTotalMrp)
                    .totalSellingPrice(sellerTotalSelling)
                    .totalAmount(sellerTotalSelling.subtract(couponShare))
                    .discount(discountPercentage)
                    .couponCode(appliedCoupon)
                    .couponDiscount(couponShare)
                    .checkoutRef(checkoutRef)
                    .expiresAt(expiresAt)
                    .build();

            // Map and bind order items to this order
            List<OrderItem> orderItems = sellerCartItems.stream().map(cartItem ->
                    OrderItem.builder()
                            .order(order)
                            .productId(cartItem.getProductId())
                            .productName(cartItem.getProductName())
                            .productImage(cartItem.getImageUrl())
                            .variantId(cartItem.getVariantId())
                            .variantLabel(cartItem.getVariantLabel())
                            .size(cartItem.getSize())
                            .quantity(cartItem.getQuantity())
                            .unitPrice(cartItem.getUnitSellingPrice())
                            .mrpPrice(cartItem.getTotalMrpPrice())
                            .sellingPrice(cartItem.getTotalSellingPrice())
                            .sellerId(sellerId)
                            .build()
            ).toList();

            order.setItems(orderItems);

            Order savedOrder = orderRepository.save(order);
            orders.add(savedOrder);
            log.info("Order {} saved for user {}", savedOrder.getId(), user.getEmail());

            // Written in the SAME transaction as the order row above (OutboxEventWriter is
            // just a JPA save, no Kafka call) - so a DB commit and "there is an event to
            // publish" always happen atomically. OrderOutboxPoller publishes it to Kafka
            // afterwards, out-of-band. (OrderEventProducer, which used to be called directly
            // here, is now dead code - kept only as the poller's Avro-building reference.)
            outboxEventWriter.write(
                    "OrderCreatedEvent",
                    ORDER_CREATED_TOPIC,
                    String.valueOf(savedOrder.getId()),
                    new OrderCreatedFields(
                            savedOrder.getId(),
                            savedOrder.getUserId(),
                            savedOrder.getItems().stream()
                                    .map(i -> new OrderCreatedFields.ItemField(i.stockId(), i.getQuantity(), i.getUnitPrice()))
                                    .toList(),
                            savedOrder.getTotalAmount(),
                            savedOrder.getCreatedAt()
                    )
            );
        }

        clearCartAfterCommit();

        return orders.stream().map(this::toResponse).toList();
    }

    /**
     * Empties the cart only once the orders are durably committed, so a failed/rolled-back order never
     * loses the customer's cart. A cart-service hiccup must NOT fail an order that is already saved, so
     * errors are only logged (the customer can still remove stale items manually; clearCart is idempotent).
     */
    private void clearCartAfterCommit() {
        Runnable clear = () -> {
            try {
                cartApiClient.clearCart();
            } catch (Exception ex) {
                log.warn("Orders were placed but the cart could not be cleared: {}", ex.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    clear.run();
                }
            });
        } else {
            clear.run();
        }
    }

    /**
     * Uses the coupon for this checkout and returns each seller's share of the discount.
     * Not valid any more (expired, limit reached, cart changed...) -> 400 with the reason, nothing is saved.
     */
    private Map<Long, BigDecimal> redeemCoupon(String code, String checkoutRef, Map<Long, List<CartItemResponse>> itemsBySeller) {
        List<CouponLine> lines = itemsBySeller.entrySet().stream()
                .map(e -> new CouponLine(e.getKey(), e.getValue().stream()
                        .map(CartItemResponse::getTotalSellingPrice)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();

        CouponResult result;
        try {
            result = couponApiClient.redeem(new RedeemRequest(checkoutRef, code, lines)).getData();
        } catch (Exception e) {
            releaseCouponQuietly(checkoutRef); // we cannot tell whether the use was recorded; giving it back is harmless if not
            throw new ServiceUnavailableException("Could not verify your coupon right now. Please try again shortly.", e);
        }
        if (result == null || !result.isValid()) {
            String reason = result == null || result.getMessage() == null ? "it is not valid" : result.getMessage();
            throw new BadRequestException("Coupon " + code + " could not be applied: " + reason + " Remove it from your cart and try again.");
        }

        // From here on the use is counted. Whatever goes wrong before the commit, give it back.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        releaseCouponQuietly(checkoutRef);
                    }
                }
            });
        }

        Map<Long, BigDecimal> shares = new HashMap<>();
        if (result.getAllocations() != null) {
            result.getAllocations().forEach(a -> shares.merge(a.getSellerId(), a.getAmount(), BigDecimal::add));
        }
        return shares;
    }

    /** When EVERY order of a checkout is cancelled the coupon use goes back to the customer (after the commit). */
    private void releaseCouponIfCheckoutFullyCancelled(Order order) {
        if (order.getCheckoutRef() == null || order.getCouponCode() == null) {
            return;
        }
        boolean allCancelled = orderRepository.findByCheckoutRef(order.getCheckoutRef()).stream()
                .allMatch(o -> o.getStatus() == OrderStatus.CANCELLED);
        if (allCancelled) {
            String ref = order.getCheckoutRef();
            runAfterCommit(() -> releaseCouponQuietly(ref));
        }
    }

    /** Never throws: a coupon that stays "used" is a small annoyance, a failed cancellation is a bug. */
    private void releaseCouponQuietly(String checkoutRef) {
        try {
            couponApiClient.release(new ReleaseRequest(checkoutRef));
        } catch (Exception ex) {
            log.warn("Could not release coupon use for checkout {}: {}", checkoutRef, ex.getMessage());
        }
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private int calculateDiscount(BigDecimal mrp, BigDecimal selling) {
        if (mrp == null || mrp.compareTo(BigDecimal.ZERO) <= 0 || mrp.compareTo(selling) <= 0) {
            return 0;
        }
        return mrp.subtract(selling)
                .divide(mrp, 2, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .intValue();
    }

    public OrderResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    /** Same as {@link #getById(Long)} but only for the customer who owns the order, its seller, or an admin. */
    public OrderResponse getByIdForCaller(Long id, GatewayUser caller) {
        Order order = findOrThrow(id);
        boolean owner = order.getUserId().equals(caller.id());
        boolean seller = order.getSellerId() != null && order.getSellerId().equals(caller.id());
        if (!owner && !seller && !caller.isAdmin()) {
            throw new ForbiddenOperationException("This order belongs to another customer.");
        }
        return toResponse(order);
    }

    /** Used by payment-service: every order of one checkout (internal API, no caller). */
    public List<OrderResponse> getByCheckoutRef(String checkoutRef) {
        return orderRepository.findByCheckoutRef(checkoutRef).stream().map(this::toResponse).toList();
    }

    public List<OrderResponse> getByUser(Long userId) {
        return orderRepository.findByUserId(userId).stream().map(this::toResponse).toList();
    }

    public List<OrderResponse> getBySeller(Long sellerId) {
        return orderRepository.findBySellerId(sellerId).stream().map(this::toResponse).toList();
    }

    /** Triggered by InventoryReservedEvent. */
    @Transactional
    public void markAwaitingPayment(Long orderId) {
        Order order = findOrThrow(orderId);
        if (order.getStatus() != OrderStatus.PENDING) {
            log.info("Order {} is {} (not PENDING), ignoring AWAITING_PAYMENT transition", orderId, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.AWAITING_PAYMENT);
        orderRepository.save(order);
        log.info("Order {} -> AWAITING_PAYMENT", orderId);
    }

    /** Triggered by InventoryReservationFailedEvent. */
    @Transactional
    public void markCancelled(Long orderId, String reason) {
        cancel(orderId, reason, false, false);
    }

    /** Same, but tells the customer the item is out of stock (InventoryReservationFailedEvent). */
    @Transactional
    public void markCancelledOutOfStock(Long orderId, String reason) {
        cancel(orderId, reason, false, true);
    }

    private void cancel(Long orderId, String reason, boolean returnedFromTransit, boolean outOfStock) {
        Order order = findOrThrow(orderId);
        boolean inTransit = order.getStatus() == OrderStatus.OUT_FOR_DELIVERY && !returnedFromTransit;
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.SHIPPED
                || inTransit || order.getStatus() == OrderStatus.DELIVERED) {
            log.info("Order {} is already {}, ignoring CANCELLED transition ({})", orderId, order.getStatus(), reason);
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        order.setExpiresAt(null);
        orderRepository.save(order);
        log.info("Order {} -> CANCELLED ({})", orderId, reason);

        // Compensation, same transaction as the status change (outbox): inventory-service puts the stock back,
        // payment-service voids the unpaid payment or refunds a paid one.
        outboxEventWriter.write(
                "OrderCancelledEvent",
                ORDER_CANCELLED_TOPIC,
                String.valueOf(order.getId()),
                new OrderCancelledFields(order.getId(), order.getUserId(), reason, Instant.now())
        );
        releaseCouponIfCheckoutFullyCancelled(order);
        notificationPublisher.publish(order, outOfStock ? OrderStage.OUT_OF_STOCK : OrderStage.CANCELLED, reason);
    }

    /** Triggered by PaymentCompletedEvent. */
    @Transactional
    public void markConfirmed(Long orderId, String paymentMethod) {
        Order order = findOrThrow(orderId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            log.info("Order {} is {} (not AWAITING_PAYMENT), ignoring CONFIRMED transition", orderId, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.CONFIRMED);
        order.setExpiresAt(null); // paid (or cash on delivery chosen): no more payment window
        if (paymentMethod != null && PAYMENT_METHODS.contains(paymentMethod)) {
            order.setPaymentMethod(paymentMethod);
        }
        orderRepository.save(order);
        log.info("Order {} -> CONFIRMED ({})", orderId, paymentMethod);
        notificationPublisher.publish(order, OrderStage.ORDER_PLACED, null);
    }

    /**
     * The customer gives up before paying. Allowed only while nothing was paid (PENDING / AWAITING_PAYMENT): paid orders
     * are cancelled through the seller/shipping flow, which also knows whether the parcel already left.
     */
    @Transactional
    public void cancelUnpaidByCustomer(Long orderId, GatewayUser caller) {
        Order order = findOrThrow(orderId);
        if (!order.getUserId().equals(caller.id())) {
            throw new ForbiddenOperationException("This order belongs to another customer.");
        }
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            throw new ConflictException("Order " + orderId + " is " + order.getStatus() + " and can no longer be cancelled here.");
        }
        markCancelled(orderId, "Cancelled by customer before payment");
    }

    /** "Cancel" on the payment page: drops every still-unpaid order of the checkout. */
    @Transactional
    public void cancelCheckoutByCustomer(String checkoutRef, GatewayUser caller) {
        List<Order> orders = orderRepository.findByCheckoutRef(checkoutRef);
        if (orders.isEmpty()) {
            throw new NotFoundException("We couldn't find that checkout. Please start again from your cart.");
        }
        for (Order order : orders) {
            if (!order.getUserId().equals(caller.id())) {
                throw new ForbiddenOperationException("This checkout belongs to another customer.");
            }
        }
        for (Order order : orders) {
            if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.AWAITING_PAYMENT) {
                markCancelled(order.getId(), "Cancelled by customer before payment");
            }
        }
    }

    /** Ids of unpaid orders whose payment window is over (the scheduler cancels them one by one, each in its own transaction). */
    @Transactional(readOnly = true)
    public List<Long> findExpiredUnpaidOrderIds() {
        return orderRepository.findTop100ByStatusInAndExpiresAtBefore(
                        List.of(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT), Instant.now())
                .stream().map(Order::getId).toList();
    }

    /**
     * Triggered by ShipmentStatusChangedEvent: mirrors the shipment's progress onto the order. Each step is only
     * applied from the status(es) that may precede it, so a duplicate or out-of-order event changes nothing.
     */
    @Transactional
    public void applyShipmentStatus(Long orderId, String shipmentStatus, String note) {
        switch (shipmentStatus) {
            // READY_TO_SHIP also arrives from OUT_FOR_DELIVERY: a failed delivery attempt puts the parcel back in the pool.
            case "READY_TO_SHIP" -> moveOrder(orderId, OrderStatus.READY_TO_SHIP, OrderStatus.CONFIRMED, OrderStatus.OUT_FOR_DELIVERY);
            case "OUT_FOR_DELIVERY" -> moveOrder(orderId, OrderStatus.OUT_FOR_DELIVERY, OrderStatus.CONFIRMED, OrderStatus.READY_TO_SHIP);
            case "DELIVERED" -> moveOrder(orderId, OrderStatus.DELIVERED, OrderStatus.OUT_FOR_DELIVERY);
            // A parcel the agent could not deliver goes back to the seller: allowed even though the order was on the road.
            case "CANCELLED" -> cancel(orderId, note == null || note.isBlank() ? "Cancelled" : note, true, false);
            default -> log.debug("Order {}: shipment status {} needs no order change", orderId, shipmentStatus);
        }
    }

    private void moveOrder(Long orderId, OrderStatus target, OrderStatus... allowedFrom) {
        Order order = findOrThrow(orderId);
        if (order.getStatus() == target) {
            return;
        }
        if (!java.util.Arrays.asList(allowedFrom).contains(order.getStatus())) {
            log.info("Order {} is {}, ignoring transition to {}", orderId, order.getStatus(), target);
            return;
        }
        OrderStatus before = order.getStatus();
        order.setStatus(target);
        orderRepository.save(order);
        log.info("Order {} -> {}", orderId, target);
        notifyMoved(order, before, target);
    }

    /**
     * E-mails only the steps the customer cares about. READY_TO_SHIP after a failed delivery attempt (OUT_FOR_DELIVERY ->
     * READY_TO_SHIP) is not "packed" again, so it is not announced twice.
     */
    private void notifyMoved(Order order, OrderStatus before, OrderStatus target) {
        switch (target) {
            case READY_TO_SHIP -> {
                if (before == OrderStatus.CONFIRMED) {
                    notificationPublisher.publish(order, OrderStage.PACKED, null);
                }
            }
            case OUT_FOR_DELIVERY -> notificationPublisher.publish(order, OrderStage.OUT_FOR_DELIVERY, null);
            case DELIVERED -> notificationPublisher.publish(order, OrderStage.DELIVERED, null);
            default -> { }
        }
    }

    /** Triggered by ShipmentCreatedEvent (legacy: shipping-service no longer publishes it). */
    @Transactional
    public void markShipped(Long orderId) {
        Order order = findOrThrow(orderId);
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            log.info("Order {} is {} (not CONFIRMED), ignoring SHIPPED transition", orderId, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.SHIPPED);
        orderRepository.save(order);
        log.info("Order {} -> SHIPPED", orderId);
    }

    private Order findOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("We couldn't find that order."));
    }

    private AddressResponse shippingAddress(UserResponse user, Long requestedAddressId) {
        List<AddressResponse> addresses = user.getAddresses();

        if (addresses == null || addresses.isEmpty()) {
            throw new BadRequestException("No shipping address found. Please add an address before placing an order.");
        }

        if (requestedAddressId != null) {
            return addresses.stream()
                    .filter(address -> address.getId().equals(requestedAddressId)).findFirst()
                    .orElseThrow(() -> new BadRequestException("That address does not belong to your account."));
        }

        return addresses.stream().filter(AddressResponse::isDefault).findFirst().orElseGet(() -> {
            log.warn("No default address flagged for user {}. Falling back to first available address.", user.getId());
            return addresses.get(0);
        });
    }

    private String formatAddress(AddressResponse addr) {
        StringBuilder sb = new StringBuilder();
        sb.append(addr.getRecipientName()).append(", ");
        sb.append(addr.getStreetLine1());
        if (addr.getStreetLine2() != null && !addr.getStreetLine2().isBlank()) {
            sb.append(", ").append(addr.getStreetLine2());
        }
        sb.append(", ").append(addr.getCity())
                .append(", ").append(addr.getState())
                .append(" - ").append(addr.getPostalCode())
                .append(", ").append(addr.getCountry());
        return sb.toString();
    }

    private OrderResponse toResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .sellerId(order.getSellerId())
                .status(order.getStatus().name())
                .totalAmount(order.getTotalAmount())
                .cancellationReason(order.getCancellationReason())
                .orderDate(order.getCreatedAt())
                .couponCode(order.getCouponCode())
                .couponDiscount(order.getCouponDiscount())
                .checkoutRef(order.getCheckoutRef())
                .paymentMethod(order.getPaymentMethod())
                .shippingAddress(order.getShippingAddress())
                .expiresAt(order.getExpiresAt())
                .items(order.getItems().stream().map(i -> OrderItemResponse.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .productImage(i.getProductImage())
                        .variantId(i.getVariantId())
                        .variantLabel(i.getVariantLabel())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .build()).toList())
                .build();
    }
}

