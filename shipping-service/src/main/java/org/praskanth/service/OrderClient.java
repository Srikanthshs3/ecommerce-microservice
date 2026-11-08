package org.praskanth.service;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.OrderInfo;
import org.praskanth.exception.ServiceUnavailableException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Reads an order from order-service (internal API, never reachable through the API gateway). */
@Component
@RequiredArgsConstructor
public class OrderClient {

    private final RestTemplate restTemplate;

    public OrderInfo get(Long orderId) {
        try {
            ResponseEntity<ApiResponse<OrderInfo>> response = restTemplate.exchange(
                    "http://order-service/internal/v1/orders/" + orderId, HttpMethod.GET, null,
                    new ParameterizedTypeReference<>() {});
            ApiResponse<OrderInfo> body = response.getBody();
            if (body == null || body.getData() == null) {
                throw new ServiceUnavailableException("We couldn't load the order details right now. Please try again shortly.", new IllegalStateException("order-service returned no data for order " + orderId));
            }
            return body.getData();
        } catch (ServiceUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new ServiceUnavailableException("We couldn't load the order details right now. Please try again shortly.", ex);
        }
    }
}
