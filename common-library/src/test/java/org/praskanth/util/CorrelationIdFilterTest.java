package org.praskanth.util;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();

    @AfterEach
    void cleanMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("incoming correlation id is reused, put in MDC during the chain and echoed back")
    void incomingId_isReusedAndEchoed() throws Exception {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "abc-123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (ServletRequest req, ServletResponse res) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY));
        // When
        filter.doFilter(request, response, chain);
        // Then
        assertThat(seen.get()).isEqualTo("abc-123");
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo("abc-123");
    }

    @Test
    @DisplayName("missing header -> a fresh UUID is generated")
    void missingId_generatesUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> seen = new AtomicReference<>();
        FilterChain chain = (ServletRequest req, ServletResponse res) -> seen.set(MDC.get(CorrelationIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertThat(seen.get()).isNotBlank();
        assertThat(UUID.fromString(seen.get())).isNotNull();
        assertThat(response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER)).isEqualTo(seen.get());
    }

    @Test
    @DisplayName("blank header is treated as missing")
    void blankId_generatesUuid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ServletRequest req, ServletResponse res) -> { });

        String echoed = response.getHeader(CorrelationIdFilter.CORRELATION_ID_HEADER);
        assertThat(echoed).isNotBlank().isNotEqualTo("   ");
    }

    @Test
    @DisplayName("MDC is cleaned up after the chain, also when it throws")
    void mdcRemovedAfterChain_evenOnFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(CorrelationIdFilter.CORRELATION_ID_HEADER, "id-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain failing = (ServletRequest req, ServletResponse res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failing))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
