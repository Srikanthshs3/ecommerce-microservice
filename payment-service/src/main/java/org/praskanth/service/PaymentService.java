package org.praskanth.service;

import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.OrderLookupResponse;
import org.praskanth.dto.PaymentResponse;
import org.praskanth.entity.Payment;
import org.praskanth.entity.PaymentGateway;
import org.praskanth.entity.PaymentStatus;
import org.praskanth.gateway.PaymentGatewayStrategy;
import org.praskanth.outbox.PaymentCompletedFields;
import org.praskanth.outbox.PaymentFailedFields;
import org.praskanth.outbox.OutboxEventWriter;
import org.praskanth.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * The Saga step triggered by InventoryReservedEvent. This is a SIMULATED charge - there's no
 * real payment gateway integration here, just a configurable random-failure knob
 * (`payment.simulate.failure-rate`, default 0 = always succeeds) so you can exercise the
 * compensating-release path in inventory-service without needing a real gateway sandbox.
 * Swap simulateCharge() for a real gateway call (Stripe, Razorpay, etc.) when you're ready.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    private final PaymentRepository paymentRepository;
    private final OutboxEventWriter outboxEventWriter;
    private final RestTemplate restTemplate;
    private final Map<PaymentGateway, PaymentGatewayStrategy> gatewayStrategies;

    @Value("${payment.gateway.default:RAZORPAY}")
    private PaymentGateway defaultGateway;

    /**
     * Idempotent at the DB level too (unique constraint on order_id), on top of the Redis
     * dedup check the caller (PaymentEventConsumer) already did - belt and suspenders, since
     * these are two independent mechanisms guarding the same "don't charge twice" invariant.
     */
    @Transactional
    public void processPaymentForOrder(Long orderId) {
        if (paymentRepository.existsByOrderId(orderId)) {
            log.info("Payment already recorded for order {}, skipping (idempotent no-op)", orderId);
            return;
        }

        BigDecimal amount = lookupOrderAmount(orderId);
        PaymentGateway gateway = defaultGateway;
        PaymentGatewayStrategy strategy = gatewayStrategies.get(gateway);

        if (strategy == null) {
            String error = "No strategy registered for gateway: " + gateway;
            recordFailure(orderId, amount, gateway, error);
            return;
        }

        try {
            log.info("Initiating payment for orderId={} amount={} using gateway={}", orderId, amount, gateway);

            // Execute real payment gateway transaction
            String transactionId = strategy.executePayment(orderId, amount, "INR");

            Payment payment = Payment.builder()
                    .orderId(orderId)
                    .amount(amount)
                    .gateway(gateway)
                    .status(PaymentStatus.SUCCEEDED)
                    .transactionId(transactionId)
                    .completedAt(Instant.now())
                    .build();

            paymentRepository.save(payment);

            // Written in the SAME transaction as the Payment row above (OutboxEventWriter is
            // just a JPA save) so the DB write and "there is an event to publish" are atomic.
            // PaymentOutboxPoller publishes it to Kafka afterwards, out-of-band.
            outboxEventWriter.write(
                    "PaymentCompletedEvent",
                    PAYMENT_COMPLETED_TOPIC,
                    String.valueOf(orderId),
                    new PaymentCompletedFields(orderId, amount, transactionId, Instant.now())
            );
            log.info("Payment succeeded for order {} amount {} txn {}", orderId, amount, transactionId);

        } catch (Exception ex) {
            log.error("Payment failed for orderId={} via gateway={}", orderId, gateway, ex);
            recordFailure(orderId, amount, gateway, ex.getMessage());
        }
    }

    public PaymentResponse getByOrderId(Long orderId) {
        return paymentRepository.findByOrderId(orderId)
                .map(this::toResponse)
                .orElse(null);
    }

    private BigDecimal lookupOrderAmount(Long orderId) {
        // /internal/** : service-to-service only (not routable via the API gateway). This runs on a Kafka consumer thread,
        // so there is no user and no identity headers to forward.
        String url = "http://order-service/internal/v1/orders/" + orderId;
        ResponseEntity<ApiResponse<OrderLookupResponse>> response = restTemplate.exchange(
                url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});

        ApiResponse<OrderLookupResponse> body = response.getBody();
        if (body == null || body.getData() == null) {
            throw new IllegalStateException("order-service returned no data for order " + orderId);
        }
        return body.getData().getTotalAmount();
    }

    private void recordFailure(Long orderId, BigDecimal amount, PaymentGateway gateway, String reason) {
        Payment payment = Payment.builder()
                .orderId(orderId)
                .amount(amount)
                .gateway(gateway)
                .status(PaymentStatus.FAILED)
                .failureReason(reason)
                .completedAt(Instant.now())
                .build();

        paymentRepository.save(payment);

        // Compensating event for inventory & order service - same atomic outbox write as the
        // success path above (PaymentEventProducer, called directly here before, is now dead
        // code; see PaymentOutboxPoller).
        outboxEventWriter.write(
                "PaymentFailedEvent",
                PAYMENT_FAILED_TOPIC,
                String.valueOf(orderId),
                new PaymentFailedFields(orderId, reason)
        );
        log.warn("Payment failed event queued (outbox) for order {}: {}", orderId, reason);
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .orderId(payment.getOrderId())
                .amount(payment.getAmount())
                .status(payment.getStatus().name())
                .transactionId(payment.getTransactionId())
                .failureReason(payment.getFailureReason())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}
