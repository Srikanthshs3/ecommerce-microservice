package org.praskanth.config;

import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Everything the gateway says when IT (not a service) has to answer with an error: wording, the platform envelope
 * {@code {success:false, error:{code,message,path,reference?,timestamp}}}, and which part of the shop the route belongs to,
 * so "payments are temporarily unavailable" can be said instead of "503".
 *
 * The api-gateway does not depend on common-library (it is reactive), so this mirrors its ErrorMessages on purpose.
 */
final class GatewayErrors {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private GatewayErrors() {
    }

    /** "Payments", "Your cart", ... derived from the route id (api-gateway/config-repo API-GATEWAY.yml); null when unknown. */
    static String area(ServerWebExchange exchange) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null || route.getId() == null) {
            return null;
        }
        String id = route.getId().toLowerCase(Locale.ROOT);
        if (id.contains("payment")) return "Payments";
        if (id.contains("order")) return "Orders";
        if (id.contains("cart")) return "Your cart";
        if (id.contains("product")) return "The product catalogue";
        if (id.contains("category")) return "Categories";
        if (id.contains("inventory")) return "Stock information";
        if (id.contains("shipping")) return "Delivery tracking";
        if (id.contains("coupon")) return "Coupons";
        if (id.contains("review")) return "Reviews";
        if (id.contains("notification")) return "Notifications";
        if (id.contains("auth")) return "Sign-in";
        if (id.contains("user")) return "Your account";
        return null;
    }

    static String unavailable(String area) {
        return area == null
                ? "This part of the shop is temporarily unavailable. Please try again in a few moments."
                : area + (area.endsWith("s") && !area.startsWith("Your") && !area.startsWith("The")
                        ? " are temporarily unavailable. " : " is temporarily unavailable. ")
                  + "Please try again in a few moments.";
    }

    static String timedOut(String area) {
        return (area == null ? "This" : area) + " is taking longer than expected. Please try again in a moment.";
    }

    static String forStatus(int status, String area) {
        return switch (status) {
            case 400 -> "We couldn't understand that request. Please check it and try again.";
            case 401 -> "Please sign in to continue.";
            case 403 -> "You don't have permission to do that.";
            case 404 -> "We couldn't find what you were looking for.";
            case 405 -> "That action isn't available here.";
            case 413 -> "The file is too large. Please choose a smaller one and try again.";
            case 415 -> "That type of file or format isn't supported.";
            case 429 -> "You're going a little too fast. Please wait a moment and try again.";
            case 502, 503 -> unavailable(area);
            case 504 -> timedOut(area);
            default -> status >= 500
                    ? "Something went wrong on our side. Please try again in a moment."
                    : "We couldn't complete that request. Please try again.";
        };
    }

    static String codeFor(int status) {
        return switch (status) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "UNAUTHORIZED";
            case 403 -> "FORBIDDEN";
            case 404 -> "NOT_FOUND";
            case 405 -> "METHOD_NOT_ALLOWED";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 429 -> "TOO_MANY_REQUESTS";
            case 502, 503 -> "SERVICE_UNAVAILABLE";
            case 504 -> "GATEWAY_TIMEOUT";
            default -> status >= 500 ? "INTERNAL_ERROR" : "BAD_REQUEST";
        };
    }

    static String newReference() {
        StringBuilder sb = new StringBuilder(8);
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** The path the browser asked for (after a forward to /fallback the request itself says "/fallback"). */
    static String originalPath(ServerWebExchange exchange) {
        Set<URI> original = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ORIGINAL_REQUEST_URL_ATTR);
        if (original != null && !original.isEmpty()) {
            return original.iterator().next().getPath();
        }
        return exchange.getRequest().getURI().getPath();
    }

    static Map<String, Object> body(String code, String message, String path, String reference) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", message);
        error.put("path", path);
        if (reference != null) {
            error.put("reference", reference);
        }
        error.put("timestamp", Instant.now().toString());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", error);
        return body;
    }
}
