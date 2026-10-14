package org.praskanth.config;

import lombok.RequiredArgsConstructor;
import org.praskanth.dto.CouponLine;
import org.praskanth.dto.CouponResult;
import org.praskanth.dto.RemoteResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CouponApiClient {

    private final RestTemplate restTemplate;

    /**
     * "What would this code save on these items?" The customer's identity is forwarded automatically (common-library
     * adds X-User-* to every load-balanced RestTemplate), which coupon-service needs for per-customer limits.
     * Nothing is recorded on the coupon-service side.
     */
    public CouponResult validate(String code, List<CouponLine> lines) {
        RemoteResponse<CouponResult> body = restTemplate.exchange(
                "http://coupon-service/internal/v1/coupon/validate",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("code", code, "lines", lines)),
                new ParameterizedTypeReference<RemoteResponse<CouponResult>>() {}).getBody();
        if (body == null || body.getData() == null) {
            throw new IllegalStateException("Empty answer from coupon-service");
        }
        return body.getData();
    }
}
