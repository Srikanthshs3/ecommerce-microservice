package org.praskanth.service;

import org.praskanth.dto.InventoryResponse;
import org.praskanth.dto.ReservationItem;
import org.praskanth.entity.Inventory;
import org.praskanth.entity.OrderReservation;
import org.praskanth.exception.ConflictException;
import org.praskanth.exception.NotFoundException;
import org.praskanth.repository.InventoryRepository;
import org.praskanth.repository.OrderReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stock changes (reserve/release/confirm) are read-modify-write on a single row guarded by
 * @Version (optimistic locking). Under contention on a hot product, two concurrent requests can
 * race and one loses with an ObjectOptimisticLockingFailureException - rather than surface that
 * as a 500 to the caller, we retry a few times with the freshly-reloaded row, which is safe here
 * because each retry re-checks available stock from scratch.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private static final int MAX_RETRIES = 3;

    private final InventoryRepository inventoryRepository;
    private final OrderReservationRepository orderReservationRepository;

    /** Called by the Kafka consumer when a new product is created - starts every product at zero stock. */
    @Transactional
    public void provisionIfAbsent(String productId, String productName, Long sellerId) {
        if (inventoryRepository.existsByProductId(productId)) {
            log.debug("Inventory record already exists for productId={}", productId);
            return;
        }
        Inventory inventory = Inventory.builder()
                .productId(productId)
                .productName(productName)
                .sellerId(sellerId)
                .availableQuantity(0)
                .reservedQuantity(0)
                .build();
        inventoryRepository.save(inventory);
        log.info("Provisioned zero-stock inventory row for product {} (name='{}', sellerId={})",
                productId, productName, sellerId);
    }

    public InventoryResponse getByProductId(String productId) {
        return toResponse(findOrThrow(productId));
    }

    public List<InventoryResponse> getAll() {
        return inventoryRepository.findAll().stream().map(this::toResponse).toList();
    }

    public InventoryResponse restock(String productId, int quantity) {
        return withRetry(productId, inv -> {
            inv.setAvailableQuantity(inv.getAvailableQuantity() + quantity);
            inv.setUpdatedAt(Instant.now());
        });
    }

    /**
     * Saga entry point, triggered by OrderCreatedEvent: reserves every line item for this order.
     *
     * This is its own "local saga" across multiple product rows: reserve() on product 3 of 5
     * failing after products 1-2 already succeeded is exactly the same kind of partial-failure
     * problem the overall Kafka Saga solves at the service level - here it's solved the same
     * way, just in-process: whatever was already reserved gets released again before the
     * ConflictException is allowed to propagate, so the caller always sees "all or nothing".
     *
     * Idempotent via the OrderReservation ledger: if this orderId already has ANY reservation
     * rows, this is a redelivery of an already-processed OrderCreatedEvent, so it's a no-op
     * (on top of the Redis dedup the caller/consumer already did - belt and suspenders).
     */
    public void reserveForOrder(Long orderId, List<ReservationItem> items) {
        if (orderReservationRepository.existsByOrderId(orderId)) {
            log.info("Order {} already has reservation records, skipping (idempotent no-op)", orderId);
            return;
        }

        List<OrderReservation> succeeded = new ArrayList<>();
        try {
            for (ReservationItem item : items) {
                reserve(item.productId(), item.quantity());

                OrderReservation reservation = OrderReservation.builder()
                        .orderId(orderId)
                        .productId(item.productId())
                        .quantity(item.quantity())
                        .status(OrderReservation.ReservationStatus.RESERVED)
                        .build();
                orderReservationRepository.save(reservation);
                succeeded.add(reservation);
            }
        } catch (ConflictException ex) {
            log.warn("Reservation failed for order {} on product - rolling back {} already-reserved item(s)",
                    orderId, succeeded.size());
            for (OrderReservation reservation : succeeded) {
                release(reservation.getProductId(), reservation.getQuantity());
                reservation.setStatus(OrderReservation.ReservationStatus.RELEASED);
                orderReservationRepository.save(reservation);
            }
            throw ex;
        }
    }

    /** Compensating action, triggered by PaymentFailedEvent: releases every still-RESERVED line item for this order. */
    public void releaseForOrder(Long orderId) {
        List<OrderReservation> reservations =
                orderReservationRepository.findByOrderIdAndStatus(orderId, OrderReservation.ReservationStatus.RESERVED);

        if (reservations.isEmpty()) {
            log.info("No active reservations to release for order {} (already released, confirmed, or reservation itself failed)", orderId);
            return;
        }

        for (OrderReservation reservation : reservations) {
            release(reservation.getProductId(), reservation.getQuantity());
            reservation.setStatus(OrderReservation.ReservationStatus.RELEASED);
            orderReservationRepository.save(reservation);
        }
        log.info("Released {} reservation(s) for order {}", reservations.size(), orderId);
    }

    /** Finalizes the sale, triggered by ShipmentCreatedEvent: permanently deducts every still-RESERVED line item for this order. */
    public void confirmForOrder(Long orderId) {
        List<OrderReservation> reservations =
                orderReservationRepository.findByOrderIdAndStatus(orderId, OrderReservation.ReservationStatus.RESERVED);

        if (reservations.isEmpty()) {
            log.info("No active reservations to confirm for order {} (already released or confirmed)", orderId);
            return;
        }

        for (OrderReservation reservation : reservations) {
            confirm(reservation.getProductId(), reservation.getQuantity());
            reservation.setStatus(OrderReservation.ReservationStatus.CONFIRMED);
            orderReservationRepository.save(reservation);
        }
        log.info("Confirmed {} reservation(s) for order {}", reservations.size(), orderId);
    }

    /** Moves stock from available -> reserved. Fails fast if there isn't enough available stock. */
    public InventoryResponse reserve(String productId, int quantity) {
        return withRetry(productId, inv -> {
            if (inv.getAvailableQuantity() < quantity) {
                throw new ConflictException(
                        "Insufficient stock for product " + productId +
                                ": requested " + quantity + ", available " + inv.getAvailableQuantity());
            }
            inv.setAvailableQuantity(inv.getAvailableQuantity() - quantity);
            inv.setReservedQuantity(inv.getReservedQuantity() + quantity);
            inv.setUpdatedAt(Instant.now());
        });
    }

    /** Compensating action: undoes a reserve() - moves stock back from reserved -> available. */
    public InventoryResponse release(String productId, int quantity) {
        return withRetry(productId, inv -> {
            int toRelease = Math.min(quantity, inv.getReservedQuantity());
            inv.setReservedQuantity(inv.getReservedQuantity() - toRelease);
            inv.setAvailableQuantity(inv.getAvailableQuantity() + toRelease);
            inv.setUpdatedAt(Instant.now());
        });
    }

    /** Finalizes a sale - reserved stock is permanently removed (never goes back to available). */
    public InventoryResponse confirm(String productId, int quantity) {
        return withRetry(productId, inv -> {
            int toConfirm = Math.min(quantity, inv.getReservedQuantity());
            inv.setReservedQuantity(inv.getReservedQuantity() - toConfirm);
            inv.setUpdatedAt(Instant.now());
        });
    }

    public boolean checkAvailability(String productId, int quantity) {
        Inventory inventory = findOrThrow(productId);
        return inventory.getAvailableQuantity() >= quantity;
    }

    private InventoryResponse withRetry(String productId, java.util.function.Consumer<Inventory> mutation) {
        int attempts = 0;
        while (true) {
            try {
                return doInTransaction(productId, mutation);
            } catch (ObjectOptimisticLockingFailureException ex) {
                attempts++;
                if (attempts >= MAX_RETRIES) {
                    log.warn("Exhausted optimistic-lock retries for product {}", productId);
                    throw new ConflictException("Stock update conflicted too many times, please retry");
                }
                log.debug("Optimistic lock conflict for product {}, retrying ({}/{})", productId, attempts, MAX_RETRIES);
            }
        }
    }

    /**
     * Not annotated @Transactional here - this is called via self-invocation from withRetry(),
     * and Spring's proxy-based AOP doesn't intercept internal calls, so an annotation here
     * would silently do nothing anyway. It's not needed: JpaRepository.save() already runs in
     * its own transaction, and each retry attempt does exactly one read + one save, so there's
     * no multi-statement unit of work that needs an explicit transaction boundary.
     */
    private InventoryResponse doInTransaction(String productId, java.util.function.Consumer<Inventory> mutation) {
        Inventory inventory = findOrThrow(productId);
        mutation.accept(inventory);
        return toResponse(inventoryRepository.save(inventory));
    }

    private Inventory findOrThrow(String productId) {
        return inventoryRepository.findByProductId(productId)
                .orElseThrow(() -> new NotFoundException("No inventory record for product: " + productId));
    }

    private InventoryResponse toResponse(Inventory inventory) {
        return InventoryResponse.builder()
                .productId(inventory.getProductId())
                .productName(inventory.getProductName())
                .sellerId(inventory.getSellerId())
                .availableQuantity(inventory.getAvailableQuantity())
                .reservedQuantity(inventory.getReservedQuantity())
                .updatedAt(inventory.getUpdatedAt())
                .build();
    }
}

