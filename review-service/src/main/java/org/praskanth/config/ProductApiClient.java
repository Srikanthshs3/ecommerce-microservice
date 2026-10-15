package org.praskanth.config;

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
    public RemoteProduct getProduct(String productId) {
        String url = "http://product-service/api/v1/product/getProductById/" + productId;
        try {
            RemoteResponse<RemoteProduct> body = restTemplate.exchange(url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<RemoteResponse<RemoteProduct>>() {}).getBody();
            if (body == null || body.getData() == null) {
                throw new NotFoundException("Product not found: " + productId);
            }
            return body.getData();
        } catch (HttpClientErrorException.NotFound e) {
            // The caller's problem (unknown productId). Connection problems / 5xx are NOT caught: the global handler maps them to 503 / 502.
            throw new NotFoundException("Product not found: " + productId);
        }
    }
}
