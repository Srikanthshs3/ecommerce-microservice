package org.praskanth.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.dto.ApiResponse;
import org.praskanth.exception.ServiceUnavailableException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;

@ExtendWith(MockitoExtension.class)
class ProductClientTest {

    @Mock
    private RestTemplate restTemplate;

    private ProductClient client() {
        return new ProductClient(restTemplate);
    }

    @Test
    @DisplayName("countProductsInCategory returns the count from the response body")
    void countProductsInCategory_validBody_returnsCount() {
        // Given
        ResponseEntity<ApiResponse<Long>> response = ResponseEntity.ok(ApiResponse.success(7L));
        doReturn(response).when(restTemplate).exchange(anyString(), eq(HttpMethod.GET), isNull(),
                any(ParameterizedTypeReference.class), eq("cat-1"));

        // When
        long count = client().countProductsInCategory("cat-1");

        // Then
        assertThat(count).isEqualTo(7L);
    }

    @Test
    @DisplayName("countProductsInCategory fails closed with 503 when the body is null")
    void countProductsInCategory_nullBody_throwsServiceUnavailable() {
        // Given
        ResponseEntity<ApiResponse<Long>> response = ResponseEntity.ok().build();
        doReturn(response).when(restTemplate).exchange(anyString(), eq(HttpMethod.GET), isNull(),
                any(ParameterizedTypeReference.class), eq("cat-1"));

        // When / Then
        assertThatThrownBy(() -> client().countProductsInCategory("cat-1"))
                .isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    @DisplayName("countProductsInCategory fails closed with 503 when the data is null")
    void countProductsInCategory_nullData_throwsServiceUnavailable() {
        // Given
        ApiResponse<Long> body = ApiResponse.success(null);
        ResponseEntity<ApiResponse<Long>> response = ResponseEntity.ok(body);
        doReturn(response).when(restTemplate).exchange(anyString(), eq(HttpMethod.GET), isNull(),
                any(ParameterizedTypeReference.class), eq("cat-1"));

        // When / Then
        assertThatThrownBy(() -> client().countProductsInCategory("cat-1"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("couldn't check whether this category");
    }

    @Test
    @DisplayName("countProductsInCategory rethrows RestClientException so retry/breaker can see it")
    void countProductsInCategory_restClientException_propagates() {
        // Given
        ResourceAccessException failure = new ResourceAccessException("connection refused");
        doThrow(failure).when(restTemplate).exchange(anyString(), eq(HttpMethod.GET), isNull(),
                any(ParameterizedTypeReference.class), eq("cat-1"));

        // When / Then
        assertThatThrownBy(() -> client().countProductsInCategory("cat-1")).isSameAs(failure);
    }

    @Test
    @DisplayName("countFallback rethrows a ServiceUnavailableException unchanged")
    void countFallback_serviceUnavailable_rethrowsSame() {
        // Given
        ServiceUnavailableException original = new ServiceUnavailableException("nope");

        // When / Then
        assertThatThrownBy(() -> client().countFallback("cat-1", original)).isSameAs(original);
    }

    @Test
    @DisplayName("countFallback wraps any other failure into a 503 with the cause attached")
    void countFallback_otherFailure_throwsServiceUnavailableWithCause() {
        // Given
        RuntimeException cause = new RuntimeException("boom");

        // When / Then
        assertThatThrownBy(() -> client().countFallback("cat-1", cause))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("unavailable")
                .hasCause(cause);
    }
}
