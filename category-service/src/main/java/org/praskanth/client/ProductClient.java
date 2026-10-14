package org.praskanth.client;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.exception.ServiceUnavailableException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Asks product-service how many products sit in a category (internal, gateway-blocked endpoint).
 * Used to refuse deleting a category that is still in use.
 *
 * Fail closed: if product-service cannot answer, the delete is refused with a 503 instead of risking orphaned products.
 */
@Component
@RequiredArgsConstructor
public class ProductClient {

    private final RestTemplate restTemplate;

    public long countProductsInCategory(String categoryId) {
        try {
            ResponseEntity<ApiResponse<Long>> response = restTemplate.exchange(
                    "http://product-service/internal/v1/products/count?categoryId={id}",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ApiResponse<Long>>() {
                    },
                    categoryId);
            ApiResponse<Long> body = response.getBody();
            if (body == null || body.getData() == null) {
                throw new ServiceUnavailableException("Could not verify whether products still use this category");
            }
            return body.getData();
        } catch (RestClientException e) {
            throw new ServiceUnavailableException(
                    "Product service is unavailable, so category usage cannot be verified. Please try again shortly.", e);
        }
    }
}
