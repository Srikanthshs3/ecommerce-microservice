package org.praskanth.config;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.ProductResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
public class ProductApiClient {

    private final RestTemplate restTemplate;

    public ProductResponse getProductById(String productId) {
        String url = "http://product-service/api/v1/product/getProductById/" + productId;

        ResponseEntity<ApiResponse<ProductResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        if (response.getBody() == null || response.getBody().getData() == null) {
            throw new RuntimeException("Product not found");
        }
        return response.getBody().getData();
    }
}
