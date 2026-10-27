package org.praskanth.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.service.OrderService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cancels orders the customer did not pay for in time (default 15 minutes). The cancellation publishes an
 * OrderCancelledEvent, so inventory-service puts the reserved stock back and payment-service closes the payment.
 *
 * Every order is cancelled in its own transaction (a call through the OrderService proxy), so one bad row cannot block
 * the others. Running on several instances at once is harmless: markCancelled ignores orders that are already cancelled.
 * (For strict single-run, add ShedLock - see the notes.)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderExpiryJob {

    private final OrderService orderService;

    @Scheduled(fixedDelayString = "${order.expiry.poll-ms:30000}", initialDelayString = "${order.expiry.initial-delay-ms:20000}")
    public void expireUnpaidOrders() {
        for (Long orderId : orderService.findExpiredUnpaidOrderIds()) {
            try {
                orderService.markCancelled(orderId, "Payment was not completed in time");
            } catch (Exception ex) {
                log.warn("Could not expire order {}: {}", orderId, ex.getMessage());
            }
        }
    }
}
