package org.praskanth.config;

import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * An ExecutorService that carries the caller's "who is calling" context onto the worker thread.
 *
 * Why it exists: the circuit breaker runs every Feign call on a worker thread. The user's identity (X-User-* headers,
 * the SecurityContext) and the log correlation id live in ThreadLocals of the HTTP request thread, so on the worker
 * thread they are EMPTY: cart-service then answers 401 "Please sign in". This wrapper copies the request
 * attributes, the SecurityContext and the MDC when a task is submitted and restores the worker thread afterwards.
 *
 * The caller waits for the result (blocking Feign call), so the request is still alive while the task runs.
 */
public class ContextPropagatingExecutorService extends AbstractExecutorService {

    private final ExecutorService delegate;

    public ContextPropagatingExecutorService(ExecutorService delegate) {
        this.delegate = delegate;
    }

    @Override
    public void execute(Runnable command) {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        SecurityContext securityContext = SecurityContextHolder.getContext();
        Map<String, String> mdc = MDC.getCopyOfContextMap();

        delegate.execute(() -> {
            RequestAttributes oldAttributes = RequestContextHolder.getRequestAttributes();
            SecurityContext oldSecurity = SecurityContextHolder.getContext();
            Map<String, String> oldMdc = MDC.getCopyOfContextMap();
            try {
                RequestContextHolder.setRequestAttributes(attributes);
                SecurityContextHolder.setContext(securityContext);
                if (mdc != null) {
                    MDC.setContextMap(mdc);
                } else {
                    MDC.clear();
                }
                command.run();
            } finally {
                if (oldAttributes != null) {
                    RequestContextHolder.setRequestAttributes(oldAttributes);
                } else {
                    RequestContextHolder.resetRequestAttributes();
                }
                SecurityContextHolder.setContext(oldSecurity);
                if (oldMdc != null) {
                    MDC.setContextMap(oldMdc);
                } else {
                    MDC.clear();
                }
            }
        });
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return delegate.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return delegate.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return delegate.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.awaitTermination(timeout, unit);
    }
}
