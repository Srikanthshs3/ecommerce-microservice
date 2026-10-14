package org.praskanth.config;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.praskanth.dto.ApiResponse;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

/**
 * Fetches the CURRENT profile of the caller (incl. addresses, which are mutable user data owned by user-service).
 * No token is passed: the gateway's X-User-* identity headers are propagated automatically by the load-balanced
 * client (see IdentityPropagation in common-library).
 */
@HttpExchange("/api/v1/user")
public interface UserApiCall {

    Logger log = LoggerFactory.getLogger(UserApiCall.class);

    @GetExchange("/profile")
    @CircuitBreaker(name = "user", fallbackMethod = "fallbackMethod")
    @Retry(name = "user")
    public ApiResponse<UserResponse> getUserProfile();

    default ApiResponse<UserResponse> fallbackMethod(Throwable throwable) {
        log.error("Fallback triggered for getUserProfile. Reason: {}", throwable.getMessage());

        // Since we cannot create an order without knowing the user's addresses,
        // we must abort the operation by throwing a custom exception.
        throw new ServiceUnavailableException("User service is currently unavailable. Please try again later.", throwable);
    }
}
