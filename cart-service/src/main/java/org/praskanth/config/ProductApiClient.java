package org.praskanth.config;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.praskanth.resilience.Fallbacks;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.ProductResponse;
import org.praskanth.exception.NotFoundException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class ProductApiClient {

    private final RestTemplate restTemplate;

    /** Product catalogue lookup: a read, so it is retried; limited to N concurrent calls so a slow catalogue cannot take all threads. */
    @Retry(name = "productService", fallbackMethod = "productFallback")
    @CircuitBreaker(name = "productService")
    @Bulkhead(name = "productService")
    public ProductResponse getProductById(String productId) {
        String url = "http://product-service/api/v1/product/getProductById/" + productId;

        ResponseEntity<ApiResponse<ProductResponse>> response;
        try {
            response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );
        } catch (HttpClientErrorException.NotFound e) {
            // product-service answered 404: that is the caller's problem (unknown productId), not a failure of ours.
            // Connection problems / 5xx are deliberately NOT caught here: the global handler maps them to 503 / 502.
            throw new NotFoundException("We couldn't find that product. It may have been removed.");
        }

        if (response.getBody() == null || response.getBody().getData() == null) {
            throw new NotFoundException("We couldn't find that product. It may have been removed.");
        }
        return response.getBody().getData();
    }

    /** 404 / business errors pass through unchanged; everything else becomes a friendly 503. */
    public ProductResponse productFallback(String productId, Throwable failure) {
        throw Fallbacks.translate("product catalogue", failure);
    }
}
