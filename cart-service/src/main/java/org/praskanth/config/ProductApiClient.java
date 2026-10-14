package org.praskanth.config;

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
            throw new NotFoundException("Product not found: " + productId);
        }

        if (response.getBody() == null || response.getBody().getData() == null) {
            throw new NotFoundException("Product not found: " + productId);
        }
        return response.getBody().getData();
    }
}
