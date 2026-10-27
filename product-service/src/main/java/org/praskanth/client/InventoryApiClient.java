package org.praskanth.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.praskanth.resilience.Fallbacks;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collection;
import java.util.Map;

/**
 * Asks inventory-service how many pieces are available. Uses its cluster-only /internal API (no login needed, the
 * gateway never exposes /internal), because the product page is visible to visitors who are not logged in.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryApiClient {

    private final RestTemplate restTemplate;

    /** stockId -> available quantity. A stock key without a row is simply missing from the result. */
    /**
     * Degrades gracefully: when inventory-service cannot answer, the product page simply shows no stock line
     * (the frontend already copes with an empty answer) instead of failing the whole page.
     */
    @Retry(name = "inventoryService", fallbackMethod = "availabilityFallback")
    @CircuitBreaker(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    public Map<String, Integer> availability(Collection<String> stockIds) {
        if (stockIds.isEmpty()) {
            return Map.of();
        }
        String url = "http://inventory-service/internal/inventory/availability?ids=" + String.join(",", stockIds);
        Map<String, Integer> result = restTemplate.exchange(url, HttpMethod.GET, null,
                new ParameterizedTypeReference<Map<String, Integer>>() {}).getBody();
        return result == null ? Map.of() : result;
    }

    public Map<String, Integer> availabilityFallback(Collection<String> stockIds, Throwable failure) {
        log.warn("Stock levels unavailable for {} variant(s): {}", stockIds.size(), failure.getClass().getSimpleName());
        return Map.of();
    }
}
