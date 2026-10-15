package org.praskanth.security.gateway;

import jakarta.servlet.http.HttpServletRequest;
import org.praskanth.util.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Identity propagation for service-to-service calls made WHILE handling an authenticated request
 * (e.g. order-service -> user-service profile, cart-service -> inventory-service).
 *
 * The gateway's headers are copied verbatim - including the signature, so the callee can verify them exactly as
 * it would for a gateway call - but ONLY if this request was actually authenticated by
 * {@link GatewayAuthenticationFilter}. Headers that were rejected as untrusted are never relayed.
 *
 * Calls made from threads with no inbound request (Kafka consumers, schedulers) carry no identity. Such calls
 * must use an /internal/** endpoint.
 */
public final class IdentityPropagation {

    /** Plug into a RestTemplate / RestClient. Load-balanced clients get it automatically (see configuration). */
    public static final ClientHttpRequestInterceptor INTERCEPTOR = (request, body, execution) -> {
        currentHeaders().forEach((name, value) -> {
            if (!request.getHeaders().containsKey(name)) {
                request.getHeaders().set(name, value);
            }
        });
        return execution.execute(request, body);
    };

    private IdentityPropagation() {
    }

    /** Headers to attach to an outgoing internal call. Also used by order-service's Feign interceptor. */
    public static Map<String, String> currentHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (auth != null && auth.getPrincipal() instanceof GatewayUser
                && attributes instanceof ServletRequestAttributes servletAttributes) {
            HttpServletRequest inbound = servletAttributes.getRequest();
            for (String name : GatewayHeaders.PROPAGATED) {
                String value = inbound.getHeader(name);
                if (value != null) {
                    headers.put(name, value);
                }
            }
        }

        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            headers.put(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
        }
        return headers;
    }
}
