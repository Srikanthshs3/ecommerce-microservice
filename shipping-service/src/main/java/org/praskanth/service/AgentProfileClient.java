package org.praskanth.service;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.praskanth.dto.ApiResponse;
import org.praskanth.exception.ServiceUnavailableException;
import org.praskanth.service.AgentDirectory.AgentInfo;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * The raw call to user-service, kept in its own bean so Resilience4j (which works through the Spring proxy, i.e. on
 * calls between beans) protects it. A read, so it is retried. Nothing is cached on purpose (a suspension must stop pickups
 * at once); an outage shows up as an exception that {@link AgentDirectory#me()} turns into HTTP 503 / 403.
 * A 404 (never applied) is NOT an outage: it is ignored by the breaker and not retried (see resilience-defaults.yml).
 */
@Component
@RequiredArgsConstructor
public class AgentProfileClient {

    private final RestTemplate restTemplate;

    @Retry(name = "userService")
    @CircuitBreaker(name = "userService")
    @Bulkhead(name = "userService")
    public AgentInfo fetchMe() {
        ResponseEntity<ApiResponse<AgentInfo>> response = restTemplate.exchange(
                "http://user-service/api/v1/delivery-agents/me", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {});
        ApiResponse<AgentInfo> body = response.getBody();
        if (body == null || body.getData() == null) {
            throw new ServiceUnavailableException("We couldn't load the delivery partner details right now. Please try again shortly.", new IllegalStateException("user-service returned no delivery partner profile"));
        }
        return body.getData();
    }
}
