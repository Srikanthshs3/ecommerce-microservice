package org.praskanth.gateway;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.exception.BadRequestException;
import org.praskanth.exception.PaymentGatewayException;
import org.praskanth.exception.PaymentVerificationException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayCallerTest {

    private static final BigDecimal AMOUNT = new BigDecimal("10.00");

    @Mock
    private GatewayExecutor executor;

    @Mock
    private PaymentGatewayStrategy strategy;

    @InjectMocks
    private GatewayCaller caller;

    /** Same simple name as Resilience4j's exception: GatewayCaller recognises it by name only. */
    static class CallNotPermittedException extends RuntimeException {
        CallNotPermittedException() {
            super("CircuitBreaker 'paymentGateway' is OPEN");
        }
    }

    static class BulkheadFullException extends RuntimeException {
        BulkheadFullException() {
            super("full");
        }
    }

    static class TimeoutException extends RuntimeException {
        TimeoutException() {
            super("slow");
        }
    }

    @AfterEach
    void clearInterruptFlag() {
        Thread.interrupted();
    }

    @Test
    void createOrder_success_returnsGatewayOrderId() {
        when(executor.createOrder(strategy, "co-1", AMOUNT, "INR")).thenReturn(CompletableFuture.completedFuture("order_1"));

        assertThat(caller.createOrder(strategy, "co-1", AMOUNT, "INR")).isEqualTo("order_1");
    }

    @Test
    void createOrder_genericFailure_becomesPaymentGatewayExceptionWithReason() {
        when(executor.createOrder(strategy, "co-1", AMOUNT, "INR"))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("boom")));

        assertThatThrownBy(() -> caller.createOrder(strategy, "co-1", AMOUNT, "INR"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("reach the payment provider")
                .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test
    void createOrder_badRequestCause_passesThroughUnchanged() {
        BadRequestException bad = new BadRequestException("invalid amount");
        when(executor.createOrder(strategy, "co-1", AMOUNT, "INR")).thenReturn(CompletableFuture.failedFuture(bad));

        assertThatThrownBy(() -> caller.createOrder(strategy, "co-1", AMOUNT, "INR")).isSameAs(bad);
    }

    @Test
    void createOrder_paymentGatewayExceptionCause_passesThroughUnchanged() {
        PaymentGatewayException known = new PaymentGatewayException("already translated", null);
        when(executor.createOrder(strategy, "co-1", AMOUNT, "INR")).thenReturn(CompletableFuture.failedFuture(known));

        assertThatThrownBy(() -> caller.createOrder(strategy, "co-1", AMOUNT, "INR")).isSameAs(known);
    }

    @Test
    void createOrder_circuitOpenThrownByProxy_isTranslatedToFriendlyMessage() {
        when(executor.createOrder(strategy, "co-1", AMOUNT, "INR")).thenThrow(new CallNotPermittedException());

        assertThatThrownBy(() -> caller.createOrder(strategy, "co-1", AMOUNT, "INR"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("temporarily unavailable");
    }

    @Test
    void createOrder_bulkheadFull_isTranslatedToRetryMessage() {
        when(executor.createOrder(strategy, "co-1", AMOUNT, "INR")).thenThrow(new BulkheadFullException());

        assertThatThrownBy(() -> caller.createOrder(strategy, "co-1", AMOUNT, "INR"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("Please try again in a moment");
    }

    @Test
    void createOrder_timeout_isTranslatedToTookTooLongMessage() {
        when(executor.createOrder(strategy, "co-1", AMOUNT, "INR")).thenThrow(new TimeoutException());

        assertThatThrownBy(() -> caller.createOrder(strategy, "co-1", AMOUNT, "INR"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("taking too long");
    }

    @Test
    void createOrder_interrupted_restoresInterruptFlagAndThrowsGatewayException() {
        // A future that never completes: get() on an interrupted thread throws InterruptedException at once.
        when(executor.createOrder(strategy, "co-1", AMOUNT, "INR")).thenReturn(new CompletableFuture<String>());
        Thread.currentThread().interrupt();

        assertThatThrownBy(() -> caller.createOrder(strategy, "co-1", AMOUNT, "INR"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("interrupted");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void verify_success_returnsVerifiedPayment() {
        Map<String, String> proof = new HashMap<>();
        GatewayExecutor.Outcome<VerifiedPayment> outcome =
                new GatewayExecutor.Outcome<>(new VerifiedPayment("pay_1"), null);
        when(executor.verify(strategy, "o1", AMOUNT, proof)).thenReturn(CompletableFuture.completedFuture(outcome));

        assertThat(caller.verify(strategy, "o1", AMOUNT, proof)).isEqualTo(new VerifiedPayment("pay_1"));
    }

    @Test
    void verify_rejectedOutcome_throwsTheRejection() {
        Map<String, String> proof = new HashMap<>();
        PaymentVerificationException rejection = new PaymentVerificationException("wrong signature");
        GatewayExecutor.Outcome<VerifiedPayment> outcome = new GatewayExecutor.Outcome<>(null, rejection);
        when(executor.verify(strategy, "o1", AMOUNT, proof)).thenReturn(CompletableFuture.completedFuture(outcome));

        assertThatThrownBy(() -> caller.verify(strategy, "o1", AMOUNT, proof)).isSameAs(rejection);
    }

    @Test
    void verify_gatewayFailure_becomesPaymentGatewayException() {
        Map<String, String> proof = new HashMap<>();
        when(executor.verify(strategy, "o1", AMOUNT, proof))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("503 from provider")));

        assertThatThrownBy(() -> caller.verify(strategy, "o1", AMOUNT, proof))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("reach the payment provider");
    }

    @Test
    void refund_success_returnsRefundId() {
        when(executor.refund(strategy, "pay_1", AMOUNT, "key")).thenReturn(CompletableFuture.completedFuture("rf_1"));

        assertThat(caller.refund(strategy, "pay_1", AMOUNT, "key")).isEqualTo("rf_1");
    }

    @Test
    void refund_failure_becomesPaymentGatewayException() {
        when(executor.refund(strategy, "pay_1", AMOUNT, "key"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("declined")));

        assertThatThrownBy(() -> caller.refund(strategy, "pay_1", AMOUNT, "key"))
                .isInstanceOf(PaymentGatewayException.class)
                .hasMessageContaining("reach the payment provider");
    }

    @Test
    void cancelOrderQuietly_success_doesNotThrow() {
        when(executor.cancelOrder(strategy, "o1")).thenReturn(CompletableFuture.completedFuture(null));

        assertThatCode(() -> caller.cancelOrderQuietly(strategy, "o1")).doesNotThrowAnyException();
    }

    @Test
    void cancelOrderQuietly_failure_isSwallowed() {
        when(executor.cancelOrder(strategy, "o1"))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("cannot close")));

        assertThatCode(() -> caller.cancelOrderQuietly(strategy, "o1")).doesNotThrowAnyException();
    }
}
