package org.praskanth.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ContextPropagatingExecutorServiceTest {

    private final ExecutorService pool = Executors.newSingleThreadExecutor();
    private final ContextPropagatingExecutorService executor = new ContextPropagatingExecutorService(pool);

    @AfterEach
    void clean() {
        pool.shutdownNow();
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    private record Seen(String header, String user, String correlation) {}

    private final Callable<Seen> probe = () -> {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return new Seen(attrs == null ? null : attrs.getRequest().getHeader("X-User-Id"),
                auth == null ? null : String.valueOf(auth.getPrincipal()), MDC.get("correlationId"));
    };

    @Test
    void workerThreadSeesTheCallersRequestSecurityContextAndMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("asha", "pw"));
        MDC.put("correlationId", "abc-123");

        Seen seen = executor.submit(probe).get(5, TimeUnit.SECONDS);

        assertThat(seen.header()).isEqualTo("5");
        assertThat(seen.user()).isEqualTo("asha");
        assertThat(seen.correlation()).isEqualTo("abc-123");
    }

    @Test
    void contextDoesNotLeakIntoTheNextTask() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "5");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        executor.submit(probe).get(5, TimeUnit.SECONDS);

        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        Seen second = executor.submit(probe).get(5, TimeUnit.SECONDS);

        assertThat(second.header()).isNull();
        assertThat(second.user()).isNull();
    }
}
