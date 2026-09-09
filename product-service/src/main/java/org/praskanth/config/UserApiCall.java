package org.praskanth.config;

import org.praskanth.dto.UserResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.praskanth.dto.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/api/v1/user")
public interface UserApiCall {

    Logger log = LoggerFactory.getLogger(UserApiCall.class);

    @GetExchange("/profile")
    @CircuitBreaker(name = "user", fallbackMethod = "fallbackMethod")
    @Retry(name = "user")
    public ApiResponse<UserResponse> getUserProfile(@RequestHeader("Authorization") String jwt);

    /*default boolean fallbackMethod(String jwt, Throwable throwable) {
        log.info("Fallback method called for getUserProfile with jwt: {}. Error: {}", jwt, throwable.getMessage());
        return false;
    }*/

    default ApiResponse<UserResponse> fallbackMethod(String jwt, Throwable throwable) {
        log.error("Fallback triggered for getUserProfile with JWT. Reason: {}", throwable.getMessage());

        // Since we cannot create a product without knowing who the user is,
        // we must abort the operation by throwing a custom exception.
        throw new RuntimeException("User service is currently unavailable. Please try again later.", throwable);
    }
}
