package org.praskanth.gateway;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.PaymentGatewayException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

/**
 * What PaymentService and PaymentSettlementService call. Same operations as PaymentGatewayStrategy, but each one runs
 * through GatewayExecutor (circuit breaker + time limit + bulkhead) and every failure comes out as ONE exception type:
 * PaymentGatewayException (HTTP 503, "try again"). A rejected proof still comes out as PaymentVerificationException (400).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GatewayCaller {

    private final GatewayExecutor executor;

    public String createOrder(PaymentGatewayStrategy strategy, String checkoutRef, BigDecimal amount, String currency) {
        return await("create the payment", () -> executor.createOrder(strategy, checkoutRef, amount, currency));
    }

    public VerifiedPayment verify(PaymentGatewayStrategy strategy, String gatewayOrderId, BigDecimal expectedAmount,
                                  Map<String, String> proof) {
        GatewayExecutor.Outcome<VerifiedPayment> outcome =
                await("verify the payment", () -> executor.verify(strategy, gatewayOrderId, expectedAmount, proof));
        if (outcome.rejected() != null) {
            throw outcome.rejected();
        }
        return outcome.value();
    }

    public String refund(PaymentGatewayStrategy strategy, String gatewayPaymentId, BigDecimal amount, String idempotencyKey) {
        return await("refund the payment", () -> executor.refund(strategy, gatewayPaymentId, amount, idempotencyKey));
    }

    /** Best effort: closing an unpaid gateway order must never break the flow that called it. */
    public void cancelOrderQuietly(PaymentGatewayStrategy strategy, String gatewayOrderId) {
        try {
            await("close the gateway order", () -> executor.cancelOrder(strategy, gatewayOrderId));
        } catch (RuntimeException ex) {
            log.warn("Could not close gateway order {}: {}", gatewayOrderId, ex.getMessage());
        }
    }

    private <T> T await(String what, Supplier<CompletableFuture<T>> call) {
        try {
            return call.get().get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new PaymentGatewayException("The payment was interrupted. Please try again.", ex);
        } catch (ExecutionException | CompletionException ex) {
            throw translate(what, ex.getCause() != null ? ex.getCause() : ex);
        } catch (RuntimeException ex) {   // circuit open / bulkhead full / time limit thrown straight by the proxy
            throw translate(what, ex);
        }
    }

    private static RuntimeException translate(String what, Throwable failure) {
        if (failure instanceof BadRequestException bad) {
            return bad;
        }
        if (failure instanceof PaymentGatewayException known) {
            return known;
        }
        String name = failure.getClass().getSimpleName();
        String reason = switch (name) {
            case "CallNotPermittedException" -> "The payment provider is temporarily unavailable. Please try again in a few moments.";
            case "BulkheadFullException" -> "Many payments are being processed right now. Please try again in a moment.";
            case "TimeoutException" -> "The payment provider is taking too long to answer. Please try again in a moment.";
            default -> "We couldn't reach the payment provider right now. Please try again in a moment.";
        };
        // the technical reason stays in the cause (it is logged); the customer only gets the friendly sentence
        return new PaymentGatewayException(reason, failure);
    }
}
