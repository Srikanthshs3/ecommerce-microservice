package org.praskanth.config;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.praskanth.resilience.Fallbacks;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.RemoteOrder;
import org.praskanth.dto.RemoteResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class OrderApiClient {

    /** Statuses that mean "the customer really bought it" (paid or beyond). PENDING / CANCELLED orders do not count. */
    private static final Set<String> PURCHASED = Set.of("CONFIRMED", "SHIPPED", "DELIVERED");

    private final RestTemplate restTemplate;

    /** Any failure (order-service down, ...) propagates; the caller decides whether that blocks the review. */
    @Retry(name = "orderService", fallbackMethod = "purchasedFallback")
    @CircuitBreaker(name = "orderService")
    @Bulkhead(name = "orderService")
    public boolean hasPurchased(Long userId, String productId) {
        String url = "http://order-service/api/v1/order/user/" + userId;
        RemoteResponse<List<RemoteOrder>> body = restTemplate.exchange(url, HttpMethod.GET, null,
                new ParameterizedTypeReference<RemoteResponse<List<RemoteOrder>>>() {}).getBody();
        if (body == null || body.getData() == null) {
            return false;
        }
        return body.getData().stream()
                .filter(o -> o.getStatus() != null && PURCHASED.contains(o.getStatus()))
                .filter(o -> o.getItems() != null)
                .anyMatch(o -> o.getItems().stream().anyMatch(i -> productId.equals(i.getProductId())));
    }

    public boolean purchasedFallback(Long userId, String productId, Throwable failure) {
        throw Fallbacks.translate("order service", failure);
    }
}
