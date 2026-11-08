package org.praskanth.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.exception.PaymentVerificationException;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/** Resilience4j annotations are inert here (no Spring proxy): we test the plain adapter logic. */
@ExtendWith(MockitoExtension.class)
class GatewayExecutorTest {

    @Mock
    private PaymentGatewayStrategy strategy;

    private GatewayExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new GatewayExecutor();
    }

    @Test
    void createOrder_success_completesWithGatewayOrderId() throws Exception {
        when(strategy.createOrder("co-1", new BigDecimal("10.00"), "INR")).thenReturn("order_1");

        CompletableFuture<String> future = executor.createOrder(strategy, "co-1", new BigDecimal("10.00"), "INR");

        assertThat(future.get()).isEqualTo("order_1");
    }

    @Test
    void createOrder_strategyThrows_futureFailsWithCause() throws Exception {
        when(strategy.createOrder("co-1", new BigDecimal("10.00"), "INR")).thenThrow(new Exception("network down"));

        CompletableFuture<String> future = executor.createOrder(strategy, "co-1", new BigDecimal("10.00"), "INR");

        assertThat(future).isCompletedExceptionally();
        assertThatThrownBy(future::get)
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseMessage("network down");
    }

    @Test
    void verify_valid_returnsOutcomeWithValueAndNoRejection() throws Exception {
        Map<String, String> proof = new HashMap<>();
        proof.put("paymentIntentId", "pi_1");
        when(strategy.verify("pi_1", new BigDecimal("5.00"), proof)).thenReturn(new VerifiedPayment("pi_1"));

        GatewayExecutor.Outcome<VerifiedPayment> outcome =
                executor.verify(strategy, "pi_1", new BigDecimal("5.00"), proof).get();

        assertThat(outcome.value()).isEqualTo(new VerifiedPayment("pi_1"));
        assertThat(outcome.rejected()).isNull();
    }

    @Test
    void verify_rejectedProof_travelsAsValueNotAsFailure() throws Exception {
        Map<String, String> proof = new HashMap<>();
        PaymentVerificationException rejection = new PaymentVerificationException("bad signature");
        when(strategy.verify("o1", new BigDecimal("5.00"), proof)).thenThrow(rejection);

        CompletableFuture<GatewayExecutor.Outcome<VerifiedPayment>> future =
                executor.verify(strategy, "o1", new BigDecimal("5.00"), proof);

        assertThat(future).isCompleted();
        GatewayExecutor.Outcome<VerifiedPayment> outcome = future.get();
        assertThat(outcome.value()).isNull();
        assertThat(outcome.rejected()).isSameAs(rejection);
    }

    @Test
    void verify_unexpectedFailure_futureFails() throws Exception {
        Map<String, String> proof = new HashMap<>();
        when(strategy.verify("o1", new BigDecimal("5.00"), proof)).thenThrow(new Exception("timeout"));

        CompletableFuture<GatewayExecutor.Outcome<VerifiedPayment>> future =
                executor.verify(strategy, "o1", new BigDecimal("5.00"), proof);

        assertThat(future).isCompletedExceptionally();
    }

    @Test
    void refund_success_completesWithRefundId() throws Exception {
        when(strategy.refund("pay_1", new BigDecimal("3.00"), "key-1")).thenReturn("rf_1");

        assertThat(executor.refund(strategy, "pay_1", new BigDecimal("3.00"), "key-1").get()).isEqualTo("rf_1");
    }

    @Test
    void refund_strategyThrows_futureFails() throws Exception {
        when(strategy.refund("pay_1", new BigDecimal("3.00"), "key-1")).thenThrow(new Exception("refund refused"));

        assertThat(executor.refund(strategy, "pay_1", new BigDecimal("3.00"), "key-1")).isCompletedExceptionally();
    }

    @Test
    void cancelOrder_success_completesAndDelegates() throws Exception {
        CompletableFuture<Void> future = executor.cancelOrder(strategy, "o1");

        assertThat(future).isCompleted();
        assertThat(future.get()).isNull();
        verify(strategy).cancelOrder("o1");
    }

    @Test
    void cancelOrder_strategyThrows_futureFails() {
        doThrow(new IllegalStateException("nope")).when(strategy).cancelOrder("o1");

        assertThat(executor.cancelOrder(strategy, "o1")).isCompletedExceptionally();
    }
}
