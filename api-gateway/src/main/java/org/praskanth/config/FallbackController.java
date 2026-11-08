package org.praskanth.config;

import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Where a route's CircuitBreaker filter sends the request when the service is slow, failing or its circuit is open
 * (fallbackUri: forward:/fallback). It tells the client WHICH of the two it was:
 *   504 GATEWAY_TIMEOUT      the service did not answer within the route's time limit
 *   503 SERVICE_UNAVAILABLE  the service is failing, or the circuit is open (we are not even trying for a few seconds)
 * Both carry Retry-After so well-behaved clients wait before trying again.
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback")
    public Mono<ResponseEntity<Map<String, Object>>> fallback(ServerWebExchange exchange) {
        Throwable cause = exchange.getAttribute(ServerWebExchangeUtils.CIRCUITBREAKER_EXECUTION_EXCEPTION_ATTR);

        boolean timeout = cause instanceof TimeoutException
                || (cause instanceof ResponseStatusException rse && rse.getStatusCode() == HttpStatus.GATEWAY_TIMEOUT)
                || (cause != null && cause.getCause() instanceof TimeoutException);

        HttpStatus status = timeout ? HttpStatus.GATEWAY_TIMEOUT : HttpStatus.SERVICE_UNAVAILABLE;
        String area = GatewayErrors.area(exchange);
        String message = timeout ? GatewayErrors.timedOut(area) : GatewayErrors.unavailable(area);

        Map<String, Object> body = GatewayErrors.body(GatewayErrors.codeFor(status.value()), message,
                GatewayErrors.originalPath(exchange), null);
        return Mono.just(ResponseEntity.status(status).header(HttpHeaders.RETRY_AFTER, "5").body(body));
    }
}
