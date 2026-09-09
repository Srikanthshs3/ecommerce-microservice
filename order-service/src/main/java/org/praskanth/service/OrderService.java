package org.praskanth.service;

import org.praskanth.config.CartApiClient;
import org.praskanth.dto.*;
import org.praskanth.entity.Order;
import org.praskanth.entity.OrderItem;
import org.praskanth.entity.OrderStatus;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.kafka.OrderEventProducer;
import org.praskanth.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    private final OrderRepository orderRepository;
    private final OrderEventProducer orderEventProducer;
    private final CartApiClient cartApiClient;

    @Transactional
    public List<OrderResponse> create(OrderRequest request, UserResponse user, String jwt) {

        AddressResponse selectedAddress = shippingAddress(user, request.getAddressId());
        String formatedAddress = formatAddress(selectedAddress);

        CartResponse cart = cartApiClient.getUserCart(jwt).getData();
        if (cart==null || cart.getCartItems() == null || cart.getCartItems().isEmpty()) {
            throw new BadRequestException("Cannot place order: Cart is empty.");
        }

        Map<Long, List<CartItemResponse>> itemsBySeller = cart.getCartItems().stream()
                .collect(Collectors.groupingBy(CartItemResponse::getSellerId));

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

            Order order = Order.builder()
                    .userId(user.getId())
                    .sellerId(sellerId)
                    .status(OrderStatus.PENDING)
                    .shippingAddressId(selectedAddress.getId())
                    .shippingAddress(formatedAddress)
                    .totalMrpPrice(sellerTotalMrp)
                    .totalSellingPrice(sellerTotalSelling)
                    .totalAmount(sellerTotalSelling)
                    .discount(discountPercentage)
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
            orderEventProducer.publishOrderCreated(savedOrder);

        }

        return orders.stream().map(this::toResponse).toList();
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
                .items(order.getItems().stream().map(i -> OrderItemResponse.builder()
                        .productId(i.getProductId())
                        .productName(i.getProductName())
                        .quantity(i.getQuantity())
                        .unitPrice(i.getUnitPrice())
                        .build()).toList())
                .build();
    }
}

