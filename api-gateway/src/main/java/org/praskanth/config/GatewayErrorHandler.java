package org.praskanth.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * Catches every error raised INSIDE the gateway (no instance registered in Eureka, connection refused, timeout,
 * unknown route, broken filter) and answers in the platform envelope with a plain-language message.
 *
 * Before this class Spring answered with its own JSON ({"timestamp","path","status","error","requestId"}), which the
 * browser could not read, so customers saw "Request failed (503)" or "HTTP 500".
 *
 * Errors the SERVICES return (404, 400, their own 503 ...) are not touched: they pass through the gateway unchanged.
 * Registering any ErrorWebExceptionHandler makes Spring Boot's default one step back.
 */
@Component
@Order(-2) // before Spring Boot's DefaultErrorWebExceptionHandler (-1)
@Slf4j
public class GatewayErrorHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public GatewayErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex); // too late to change anything: let the container close the connection
        }

        String area = GatewayErrors.area(exchange);
        int status = statusOf(ex);
        String path = GatewayErrors.originalPath(exchange);
        String reference = null;
        String message;

        if (status == 503 || status == 502) {
            status = 503;
            message = GatewayErrors.unavailable(area);
            log.warn("{} {} -> 503 {} ({})", exchange.getRequest().getMethod(), path, rootName(ex), rootMessage(ex));
        } else if (status == 504) {
            message = GatewayErrors.timedOut(area);
            log.warn("{} {} -> 504 {} ({})", exchange.getRequest().getMethod(), path, rootName(ex), rootMessage(ex));
        } else if (status >= 500) {
            reference = GatewayErrors.newReference();
            message = GatewayErrors.forStatus(500, area) + " If it keeps happening, contact support and quote reference " + reference + ".";
            status = 500;
            log.error("{} {} -> 500 [reference {}]", exchange.getRequest().getMethod(), path, reference, ex);
        } else {
            message = GatewayErrors.forStatus(status, area);
            log.warn("{} {} -> {} {}", exchange.getRequest().getMethod(), path, status, rootName(ex));
        }

        response.setStatusCode(HttpStatusCode.valueOf(status));
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (status == 503 || status == 504 || status == 429) {
            response.getHeaders().set(HttpHeaders.RETRY_AFTER, "5");
        }
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(GatewayErrors.body(GatewayErrors.codeFor(status), message, path, reference));
        } catch (JsonProcessingException e) {
            bytes = ("{\"success\":false,\"error\":{\"code\":\"INTERNAL_ERROR\",\"message\":\""
                    + GatewayErrors.forStatus(500, null) + "\"}}").getBytes(StandardCharsets.UTF_8);
        }
        response.getHeaders().setContentLength(bytes.length);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }

    /** Maps the exception chain to the status the CUSTOMER should be told about. */
    static int statusOf(Throwable ex) {
        int depth = 0;
        for (Throwable t = ex; t != null && depth < 12; t = t.getCause() == t ? null : t.getCause(), depth++) {
            if (t instanceof TimeoutException) {
                return 504;
            }
            if (t instanceof ResponseStatusException rse) {
                int code = rse.getStatusCode().value();
                if (code == HttpStatus.GATEWAY_TIMEOUT.value()) {
                    return 504;
                }
                if (code == HttpStatus.BAD_GATEWAY.value() || code == HttpStatus.SERVICE_UNAVAILABLE.value()) {
                    return 503; // e.g. "Unable to find instance for PAYMENT-SERVICE": the service is not registered / is down
                }
                return code;
            }
            String name = t.getClass().getName();
            if (name.endsWith("ReadTimeoutException") || name.endsWith("ConnectTimeoutException")
                    || name.endsWith("WriteTimeoutException")) {
                return 504;
            }
            if (t instanceof java.net.ConnectException || t instanceof java.net.UnknownHostException
                    || name.endsWith("PrematureCloseException") || name.endsWith("CallNotPermittedException")
                    || name.endsWith("ClosedChannelException") || name.endsWith("ConnectionResetException")) {
                return 503;
            }
        }
        return 500;
    }

    private static String rootName(Throwable t) {
        Throwable root = t;
        for (int i = 0; i < 12 && root.getCause() != null && root.getCause() != root; i++) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    private static String rootMessage(Throwable t) {
        Throwable root = t;
        for (int i = 0; i < 12 && root.getCause() != null && root.getCause() != root; i++) {
            root = root.getCause();
        }
        return root.getMessage();
    }
}
