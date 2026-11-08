package org.praskanth.config;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.praskanth.resilience.Fallbacks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class InventoryApiClient {

    private static final Logger log = LoggerFactory.getLogger(InventoryApiClient.class);

    private final RestTemplate restTemplate;

    /**
     * FAIL OPEN: if inventory-service cannot answer, adding to the cart is still allowed. The cart is only a wish list;
     * the real check is the stock RESERVATION when the order is placed, so nothing can be oversold because of this.
     */
    @Retry(name = "inventoryService", fallbackMethod = "availabilityFallback")
    @CircuitBreaker(name = "inventoryService")
    @Bulkhead(name = "inventoryService")
    public boolean checkAvailability(String productId, Integer requestedQuantity) {
        String url = "http://inventory-service/api/v1/inventory/" + productId + "/check?quantity=" + requestedQuantity;

        Boolean isAvailable = restTemplate.getForObject(url, Boolean.class);
        return isAvailable != null && isAvailable;
    }

    public boolean availabilityFallback(String productId, Integer requestedQuantity, Throwable failure) {
        log.warn("Stock check for {} skipped (inventory-service unavailable: {}); allowing the cart change",
                productId, failure.getClass().getSimpleName());
        return true;
    }
}
