package org.praskanth.gateway;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import org.praskanth.exception.BadRequestException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The ONLY class that really calls Razorpay / Stripe over the internet. Do not call it directly: use GatewayCaller.
 * (It is a separate bean because Resilience4j works through the Spring proxy, which a call inside one class bypasses.)
 *
 *   thread-pool bulkhead  the call runs on its own small pool (resilience4j.thread-pool-bulkhead.instances.paymentGateway)
 *   time limiter          gives up after 15 s and cancels the call
 *   circuit breaker       after repeated failures the gateway is not called for 30 s: payments fail at once
 *
 * No retry here: "create a payment order" is not safe to repeat after a timeout.
 * Methods must return CompletableFuture for the thread pool and time limiter to apply.
 */
@Component
public class GatewayExecutor {

    /** A "business" rejection (e.g. wrong signature) is not a sick gateway: it travels as a value so it never trips the circuit. */
    public record Outcome<T>(T value, BadRequestException rejected) { }

    @CircuitBreaker(name = "paymentGateway")
    @TimeLimiter(name = "paymentGateway")
    @Bulkhead(name = "paymentGateway", type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<String> createOrder(PaymentGatewayStrategy strategy, String checkoutRef, BigDecimal amount, String currency) {
        try {
            return CompletableFuture.completedFuture(strategy.createOrder(checkoutRef, amount, currency));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @CircuitBreaker(name = "paymentGateway")
    @TimeLimiter(name = "paymentGateway")
    @Bulkhead(name = "paymentGateway", type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<Outcome<VerifiedPayment>> verify(PaymentGatewayStrategy strategy, String gatewayOrderId,
                                                              BigDecimal expectedAmount, Map<String, String> proof) {
        try {
            return CompletableFuture.completedFuture(new Outcome<>(strategy.verify(gatewayOrderId, expectedAmount, proof), null));
        } catch (BadRequestException rejected) {            // PaymentVerificationException extends BadRequestException
            return CompletableFuture.completedFuture(new Outcome<>(null, rejected));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /** The refund id is idempotent (same key = same refund), so the caller may safely repeat this. */
    @CircuitBreaker(name = "paymentGateway")
    @TimeLimiter(name = "paymentGateway")
    @Bulkhead(name = "paymentGateway", type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<String> refund(PaymentGatewayStrategy strategy, String gatewayPaymentId, BigDecimal amount, String idempotencyKey) {
        try {
            return CompletableFuture.completedFuture(strategy.refund(gatewayPaymentId, amount, idempotencyKey));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @CircuitBreaker(name = "paymentGateway")
    @TimeLimiter(name = "paymentGateway")
    @Bulkhead(name = "paymentGateway", type = Bulkhead.Type.THREADPOOL)
    public CompletableFuture<Void> cancelOrder(PaymentGatewayStrategy strategy, String gatewayOrderId) {
        try {
            strategy.cancelOrder(gatewayOrderId);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
