package org.praskanth.service;

import org.praskanth.config.CartApiClient;
import org.praskanth.config.CouponApiClient;
import org.praskanth.dto.*;
import org.praskanth.entity.Order;
import org.praskanth.entity.OrderItem;
import org.praskanth.entity.OrderStatus;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.outbox.OrderCreatedFields;
import org.praskanth.outbox.OutboxEventWriter;
import org.praskanth.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private final OrderRepository orderRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final CartApiClient cartApiClient;
    private final CouponApiClient couponApiClient;

    @Transactional
    public List<OrderResponse> create(OrderRequest request, UserResponse user) {

        AddressResponse selectedAddress = shippingAddress(user, request.getAddressId());
        String formatedAddress = formatAddress(selectedAddress);

        CartResponse cart = cartApiClient.getUserCart().getData();
        if (cart==null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Cannot place order: Cart is empty.");
        }

        Map<Long, List<CartItemResponse>> itemsBySeller = cart.getCartItems().stream()
                .collect(Collectors.groupingBy(CartItemResponse::getSellerId));

        // Coupon: used (re-validated, counted) BEFORE anything is saved, so a coupon that stopped being valid
        // fails the whole checkout cleanly. If the transaction later does not commit, the use is given back.
        String appliedCoupon = null;
        String checkoutRef = null;
        Map<Long, BigDecimal> couponShares = Map.of();
        if (cart.getCouponCode() != null && !cart.getCouponCode().isBlank()) {
            checkoutRef = UUID.randomUUID().toString();
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
                    .build();

            // Map and bind order items to this order
            List<OrderItem> orderItems = sellerCartItems.stream().map(cartItem ->
                    OrderItem.builder()
                            .order(order)
                            .productId(cartItem.getProductId())
                            .productName(cartItem.getProductName())
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
                                    .map(i -> new OrderCreatedFields.ItemField(i.getProductId(), i.getQuantity(), i.getUnitPrice()))
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
        Order order = findOrThrow(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.SHIPPED) {
            log.info("Order {} is already {}, ignoring CANCELLED transition ({})", orderId, order.getStatus(), reason);
            return;
        }
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        orderRepository.save(order);
        log.info("Order {} -> CANCELLED ({})", orderId, reason);
        releaseCouponIfCheckoutFullyCancelled(order);
    }

    /** Triggered by PaymentCompletedEvent. */
    @Transactional
    public void markConfirmed(Long orderId) {
        Order order = findOrThrow(orderId);
        if (order.getStatus() != OrderStatus.AWAITING_PAYMENT) {
            log.info("Order {} is {} (not AWAITING_PAYMENT), ignoring CONFIRMED transition", orderId, order.getStatus());
            return;
        }
        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        log.info("Order {} -> CONFIRMED", orderId);
    }

    /** Triggered by ShipmentCreatedEvent. */
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
                .orElseThrow(() -> new NotFoundException("Order not found: " + id));
    }

    private AddressResponse shippingAddress(UserResponse user, Long requestedAddressId) {
        List<AddressResponse> addresses = user.getAddresses();

        if (addresses == null || addresses.isEmpty()) {
            throw new BadRequestException("No shipping address found. Please add an address before placing an order.");
        }

        if (requestedAddressId != null) {
            return addresses.stream()
                    .filter(address -> address.getId().equals(requestedAddressId)).findFirst()
                    .orElseThrow(() -> new BadRequestException("Address ID " + requestedAddressId + " does not belong to this user."));
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
                .items(order.getItems().stream().map(i -> OrderItemResponse.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .build()).toList())
                .build();
    }
}

