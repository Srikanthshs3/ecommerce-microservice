package org.praskanth.config;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.praskanth.dto.ApiResponse;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.dto.SellerProfileResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Fetches the CURRENT seller profile (incl. KYC status) of the caller from user-service. KYC is mutable data owned
 * by user-service, so it is deliberately not carried in the JWT. No token is passed: the gateway's X-User-* identity
 * headers are propagated automatically by the load-balanced client (see IdentityPropagation in common-library).
 */
@HttpExchange("/api/v1")
public interface UserApiCall {

    Logger log = LoggerFactory.getLogger(UserApiCall.class);

    @GetExchange("/sellers/me")
    @CircuitBreaker(name = "user", fallbackMethod = "fallbackMethod")
    @Retry(name = "user")
    ApiResponse<SellerProfileResponse> getMySellerProfile();

    default ApiResponse<SellerProfileResponse> fallbackMethod(Throwable throwable) {
        log.error("Fallback triggered for getMySellerProfile. Reason: {}", throwable.getMessage());

        // A product must not be created without knowing the seller's KYC status: abort the operation.
        throw new ServiceUnavailableException("User service is currently unavailable. Please try again later.", throwable);
    }
}
