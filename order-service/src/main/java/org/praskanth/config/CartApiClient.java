package org.praskanth.config;

import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.CartResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * No Authorization header: the caller's identity is forwarded as the gateway's X-User-* headers by
 * {@link FeignConfig#identityPropagationInterceptor()}.
 *
 * Resilience: timeouts, retry and the circuit breaker come from FeignConfig / ORDER-SERVICE.yml; when the call finally
 * fails, {@link CartApiClientFallbackFactory} turns it into a friendly HTTP 503. Both calls are safe to repeat (read / delete).
 */
@FeignClient(name = "cart-service", fallbackFactory = CartApiClientFallbackFactory.class)
public interface CartApiClient {

    @GetMapping("/api/v1/cart")
    ApiResponse<CartResponse> getUserCart();

    @DeleteMapping("/api/v1/cart")
    ApiResponse<Void> clearCart();
}
