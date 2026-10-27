package org.praskanth.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.CheckoutOrder;
import org.praskanth.resilience.Fallbacks;
import org.praskanth.dto.ApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * Asks order-service which orders belong to a checkout (internal API: never reachable through the API gateway).
 * A read, so it is safe to retry. Called from PaymentService (another bean), so the annotations take effect.
 * Failures come out as ServiceUnavailableException (503) via Fallbacks.translate; 404 / 400 answers pass through.
 */
@Component
@RequiredArgsConstructor
public class OrderClient {

    private final RestTemplate restTemplate;

    @Retry(name = "orderService", fallbackMethod = "findByCheckoutRefFallback")
    @CircuitBreaker(name = "orderService")
    @Bulkhead(name = "orderService")
    public List<CheckoutOrder> findByCheckoutRef(String checkoutRef) {
        String url = "http://order-service/internal/v1/orders/checkout/" + checkoutRef;
        ResponseEntity<ApiResponse<List<CheckoutOrder>>> response = restTemplate.exchange(
                url, HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        ApiResponse<List<CheckoutOrder>> body = response.getBody();
        return body == null || body.getData() == null ? List.of() : body.getData();
    }

    public List<CheckoutOrder> findByCheckoutRefFallback(String checkoutRef, Throwable failure) {
        throw Fallbacks.translate("order service", failure);
    }
}
