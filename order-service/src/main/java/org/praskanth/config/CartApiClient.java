package org.praskanth.config;

import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.CartResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * No Authorization header: the caller's identity is forwarded as the gateway's X-User-* headers by
 * {@link FeignConfig#identityPropagationInterceptor()}.
 */
@FeignClient(name = "cart-service")
public interface CartApiClient {

    @GetMapping("/api/v1/cart")
    ApiResponse<CartResponse> getUserCart();

    @DeleteMapping("/api/v1/cart")
    ApiResponse<Void> clearCart();
}
