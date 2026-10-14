package org.praskanth.config;

import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.CouponResult;
import org.praskanth.dto.RedeemRequest;
import org.praskanth.dto.ReleaseRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Service-to-service calls (the gateway never routes /internal/**). The customer's identity is forwarded as the
 * gateway's X-User-* headers by {@link FeignConfig#identityPropagationInterceptor()}; release() is called from a
 * Kafka consumer thread, where there is no user - coupon-service allows that one endpoint without identity.
 */
@FeignClient(name = "coupon-service")
public interface CouponApiClient {

    /** Validates again and uses the coupon for this checkout. Idempotent per checkoutRef. */
    @PostMapping("/internal/v1/coupon/redeem")
    ApiResponse<CouponResult> redeem(@RequestBody RedeemRequest request);

    /** Gives the use back. Safe to repeat. */
    @PostMapping("/internal/v1/coupon/release")
    ApiResponse<Void> release(@RequestBody ReleaseRequest request);
}
