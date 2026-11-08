package org.praskanth.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClientResponseException;

import java.lang.reflect.Method;

/**
 * "Another service answered with an error" - as seen by the service that called it, whether it used RestTemplate /
 * RestClient (Spring's RestClientResponseException) or Feign (FeignException, matched by name through reflection so
 * only order-service needs the Feign library).
 *
 * The GlobalExceptionHandler uses it so a 404 / 409 from cart-service reaches the customer as the SAME friendly
 * sentence cart-service wrote, instead of turning into a generic "500 Internal Server Error".
 */
record DownstreamError(int status, String message) {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_DEPTH = 8;

    /** Finds a downstream HTTP error in the cause chain, or null. {@code status <= 0} means "never got an answer". */
    static DownstreamError find(Throwable error) {
        int depth = 0;
        for (Throwable t = error; t != null && depth < MAX_DEPTH; t = t.getCause() == t ? null : t.getCause(), depth++) {
            if (t instanceof RestClientResponseException rest) {
                return new DownstreamError(rest.getStatusCode().value(), envelopeMessage(rest.getResponseBodyAsString()));
            }
            if (isFeign(t)) {
                return new DownstreamError(feignStatus(t), envelopeMessage(feignBody(t)));
            }
        }
        return null;
    }

    private static boolean isFeign(Throwable t) {
        for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
            if ("feign.FeignException".equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    private static int feignStatus(Throwable t) {
        try {
            Method m = t.getClass().getMethod("status");
            return (int) m.invoke(t);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    private static String feignBody(Throwable t) {
        try {
            Method m = t.getClass().getMethod("contentUTF8");
            Object body = m.invoke(t);
            return body == null ? null : body.toString();
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    /** The platform envelope is {success:false, error:{message:"..."}}. Anything else (HTML, empty) gives null. */
    static String envelopeMessage(String body) {
        if (body == null || body.isBlank() || body.length() > 20_000) {
            return null;
        }
        try {
            JsonNode message = JSON.readTree(body).path("error").path("message");
            String text = message.isTextual() ? message.asText().trim() : null;
            return text == null || text.isEmpty() ? null : text;
        } catch (Exception e) {
            return null;
        }
    }
}
