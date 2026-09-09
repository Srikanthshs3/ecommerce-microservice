package org.praskanth.config;

import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.CartResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "cart-service")
public interface CartApiClient {

    @GetMapping("/api/v1/cart")
    ApiResponse<CartResponse> getUserCart(@RequestHeader("Authorization") String jwt);
}
