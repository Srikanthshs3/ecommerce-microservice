package org.praskanth.config;

import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.CartResponse;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/** Runs when cart-service cannot answer (timeout, connection error, 5xx, open circuit). */
@Component
public class CartApiClientFallbackFactory implements FallbackFactory<CartApiClient> {

    @Override
    public CartApiClient create(Throwable cause) {
        return new CartApiClient() {
            @Override
            public ApiResponse<CartResponse> getUserCart() {
                throw FeignFallbacks.translate("cart service", cause);
            }

            @Override
            public ApiResponse<Void> clearCart() {
                throw FeignFallbacks.translate("cart service", cause);
            }
        };
    }
}
