package org.praskanth.config;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class InventoryApiClient {

    private final RestTemplate restTemplate;

    public boolean checkAvailability(String productId, Integer requestedQuantity) {
        String url = "http://inventory-service/api/v1/inventory/" + productId + "/check?quantity=" + requestedQuantity;

        Boolean isAvailable = restTemplate.getForObject(url, Boolean.class);
        return isAvailable != null && isAvailable;
    }
}
