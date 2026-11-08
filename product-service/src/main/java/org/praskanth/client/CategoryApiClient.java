package org.praskanth.client;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.praskanth.resilience.Fallbacks;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.CategoryResponse;
import org.praskanth.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;


@Service
@RequiredArgsConstructor
public class CategoryApiClient {
    private static final Logger log = LoggerFactory.getLogger(CategoryApiClient.class);

    private final RestTemplate restTemplate;

    @Retry(name = "categoryService", fallbackMethod = "fallbackMethod")
    @CircuitBreaker(name = "categoryService")
    @Bulkhead(name = "categoryService")
    public ApiResponse<CategoryResponse> getById(String id) {
        String url = "http://category-service/api/v1/category/" + id;

        // ParameterizedTypeReference is required to safely deserialize generic classes like ApiResponse<T>
        ResponseEntity<ApiResponse<CategoryResponse>> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<ApiResponse<CategoryResponse>>() {}
        );

        return response.getBody();
    }

    public ApiResponse<CategoryResponse> fallbackMethod(String id, Throwable throwable) {
        log.error("Category Service call failed for ID {}. Reason: {}", id, throwable.getMessage());
        throw Fallbacks.translate("category service", throwable);
    }


}
