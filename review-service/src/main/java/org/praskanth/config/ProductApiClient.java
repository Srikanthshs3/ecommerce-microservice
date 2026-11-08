package org.praskanth.config;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.praskanth.resilience.Fallbacks;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.RemoteProduct;
import org.praskanth.dto.RemoteResponse;
import org.praskanth.exception.NotFoundException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class ProductApiClient {

    private final RestTemplate restTemplate;

    /** @throws NotFoundException when product-service says there is no such product */
    @Retry(name = "productService", fallbackMethod = "productFallback")
    @CircuitBreaker(name = "productService")
    @Bulkhead(name = "productService")
    public RemoteProduct getProduct(String productId) {
        String url = "http://product-service/api/v1/product/getProductById/" + productId;
        try {
            RemoteResponse<RemoteProduct> body = restTemplate.exchange(url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<RemoteResponse<RemoteProduct>>() {}).getBody();
            if (body == null || body.getData() == null) {
                throw new NotFoundException("We couldn't find that product. It may have been removed.");
            }
            return body.getData();
        } catch (HttpClientErrorException.NotFound e) {
            // The caller's problem (unknown productId). Connection problems / 5xx are NOT caught: the global handler maps them to 503 / 502.
            throw new NotFoundException("We couldn't find that product. It may have been removed.");
        }
    }

    public RemoteProduct productFallback(String productId, Throwable failure) {
        throw Fallbacks.translate("product catalogue", failure);
    }
}
