package org.praskanth.config;

import org.praskanth.dto.ApiResponse;
import org.praskanth.dto.CouponResult;
import org.praskanth.dto.RedeemRequest;
import org.praskanth.dto.ReleaseRequest;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

/** Runs when coupon-service cannot answer. OrderService already turns a failed redeem into "try again" and releases quietly. */
@Component
public class CouponApiClientFallbackFactory implements FallbackFactory<CouponApiClient> {

    @Override
    public CouponApiClient create(Throwable cause) {
        return new CouponApiClient() {
            @Override
            public ApiResponse<CouponResult> redeem(RedeemRequest request) {
                throw FeignFallbacks.translate("coupon service", cause);
            }

            @Override
            public ApiResponse<Void> release(ReleaseRequest request) {
                throw FeignFallbacks.translate("coupon service", cause);
            }
        };
    }
}
