package org.praskanth.resilience;

import org.praskanth.exception.ServiceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpClientErrorException;

/**
 * One place that decides what a failed call to ANOTHER service means for OUR caller. Every Resilience4j fallback
 * method in the project is a single line:
 *
 * <pre>
 *   public ProductResponse productFallback(String id, Throwable t) { throw Fallbacks.translate("product catalogue", t); }
 * </pre>
 *
 * The rule: the other service ANSWERED with a "your request is wrong" (404, 400, 403 ...) or our own business
 * exception passes through unchanged (it is not an outage, it must not become a 503). Anything else - timeout,
 * connection refused, 5xx, open circuit, full bulkhead - means "temporarily unavailable" (HTTP 503 with a friendly
 * message; the details stay in the log).
 */
public final class Fallbacks {

    private static final Logger log = LoggerFactory.getLogger(Fallbacks.class);
    private static final String BUSINESS_PACKAGE = "org.praskanth.exception.";

    private Fallbacks() {
    }

    /** Returns the exception the fallback should throw. */
    public static RuntimeException translate(String dependency, Throwable failure) {
        if (isCallerProblem(failure)) {
            return (RuntimeException) failure;
        }
        log.warn("{} is unavailable: {} ({})", dependency, failure.getClass().getSimpleName(), failure.getMessage());
        return new ServiceUnavailableException(
                "We couldn't reach the " + dependency + " right now. Please try again in a moment.", failure);
    }

    /** True for answers that are NOT a sign of a sick dependency. */
    static boolean isCallerProblem(Throwable failure) {
        if (failure instanceof HttpClientErrorException) { // 4xx from the other service
            return true;
        }
        return failure instanceof RuntimeException
                && failure.getClass().getName().startsWith(BUSINESS_PACKAGE); // NotFound, BadRequest, Conflict, Forbidden, ServiceUnavailable ...
    }
}
