package org.praskanth.config;

import feign.FeignException;
import org.praskanth.resilience.Fallbacks;

/** Same rule as {@link Fallbacks}, plus Feign's own exception type: an HTTP 4xx answer is the caller's problem, not an outage. */
final class FeignFallbacks {

    private FeignFallbacks() {
    }

    static RuntimeException translate(String dependency, Throwable failure) {
        if (failure instanceof FeignException feign && feign.status() >= 400 && feign.status() < 500) {
            return feign;
        }
        return Fallbacks.translate(dependency, failure);
    }
}
