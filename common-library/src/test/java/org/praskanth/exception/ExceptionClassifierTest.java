package org.praskanth.exception;

import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionClassifierTest {

    @Test
    @DisplayName("classify: data integrity violation is DATA_CONFLICT")
    void classify_dataIntegrityViolation_dataConflict() {
        // Given
        Throwable error = new DataIntegrityViolationException("duplicate key");
        // When / Then
        assertThat(ExceptionClassifier.classify(error)).isEqualTo(ExceptionClassifier.Kind.DATA_CONFLICT);
    }

    @Test
    @DisplayName("classify: optimistic locking failure is CONCURRENT_UPDATE")
    void classify_optimisticLocking_concurrentUpdate() {
        assertThat(ExceptionClassifier.classify(new OptimisticLockingFailureException("stale")))
                .isEqualTo(ExceptionClassifier.Kind.CONCURRENT_UPDATE);
    }

    @Test
    @DisplayName("classify: pessimistic locking failure (subclass) is CONCURRENT_UPDATE")
    void classify_pessimisticLocking_concurrentUpdate() {
        assertThat(ExceptionClassifier.classify(new CannotAcquireLockException("lock timeout")))
                .isEqualTo(ExceptionClassifier.Kind.CONCURRENT_UPDATE);
    }

    @Test
    @DisplayName("classify: JPA optimistic lock exception is CONCURRENT_UPDATE")
    void classify_jpaOptimisticLock_concurrentUpdate() {
        assertThat(ExceptionClassifier.classify(new OptimisticLockException("stale")))
                .isEqualTo(ExceptionClassifier.Kind.CONCURRENT_UPDATE);
    }

    @Test
    @DisplayName("classify: ResourceAccessException is UPSTREAM_UNAVAILABLE")
    void classify_resourceAccess_upstreamUnavailable() {
        assertThat(ExceptionClassifier.classify(new ResourceAccessException("I/O error")))
                .isEqualTo(ExceptionClassifier.Kind.UPSTREAM_UNAVAILABLE);
    }

    @Test
    @DisplayName("classify: connect, timeout and unknown-host exceptions are UPSTREAM_UNAVAILABLE")
    void classify_networkErrors_upstreamUnavailable() {
        assertThat(ExceptionClassifier.classify(new ConnectException("refused")))
                .isEqualTo(ExceptionClassifier.Kind.UPSTREAM_UNAVAILABLE);
        assertThat(ExceptionClassifier.classify(new SocketTimeoutException("read timed out")))
                .isEqualTo(ExceptionClassifier.Kind.UPSTREAM_UNAVAILABLE);
        assertThat(ExceptionClassifier.classify(new UnknownHostException("nohost")))
                .isEqualTo(ExceptionClassifier.Kind.UPSTREAM_UNAVAILABLE);
    }

    @Test
    @DisplayName("classify: 'No instances available for' message is UPSTREAM_UNAVAILABLE")
    void classify_noInstancesMessage_upstreamUnavailable() {
        assertThat(ExceptionClassifier.classify(new IllegalStateException("No instances available for product-service")))
                .isEqualTo(ExceptionClassifier.Kind.UPSTREAM_UNAVAILABLE);
    }

    @Test
    @DisplayName("classify: RestClientResponseException is UPSTREAM_ERROR")
    void classify_restClientResponse_upstreamError() {
        Throwable error = new RestClientResponseException("bad gateway", 502, "Bad Gateway",
                HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        assertThat(ExceptionClassifier.classify(error)).isEqualTo(ExceptionClassifier.Kind.UPSTREAM_ERROR);
    }

    @Test
    @DisplayName("classify: unknown exception is OTHER")
    void classify_unknown_other() {
        assertThat(ExceptionClassifier.classify(new IllegalStateException("boom")))
                .isEqualTo(ExceptionClassifier.Kind.OTHER);
    }

    @Test
    @DisplayName("classify: null message and no cause is OTHER")
    void classify_nullMessage_other() {
        assertThat(ExceptionClassifier.classify(new RuntimeException())).isEqualTo(ExceptionClassifier.Kind.OTHER);
    }

    @Test
    @DisplayName("classify: walks the cause chain")
    void classify_wrappedCause_found() {
        Throwable error = new RuntimeException("outer", new RuntimeException("mid", new ConnectException("refused")));
        assertThat(ExceptionClassifier.classify(error)).isEqualTo(ExceptionClassifier.Kind.UPSTREAM_UNAVAILABLE);
    }

    @Test
    @DisplayName("classify: data conflict wins over unavailable when both are in the chain")
    void classify_dataConflictAndUnavailable_dataConflictWins() {
        Throwable error = new DataIntegrityViolationException("dup", new ConnectException("refused"));
        assertThat(ExceptionClassifier.classify(error)).isEqualTo(ExceptionClassifier.Kind.DATA_CONFLICT);
    }

    @Test
    @DisplayName("classify: concurrent update wins over unavailable and upstream error")
    void classify_concurrentBeatsUnavailable() {
        Throwable error = new ResourceAccessException("io", new java.io.IOException("Wrapped by test", new OptimisticLockingFailureException("stale")));
        assertThat(ExceptionClassifier.classify(error)).isEqualTo(ExceptionClassifier.Kind.CONCURRENT_UPDATE);
    }

    @Test
    @DisplayName("classify: unavailable wins over upstream error")
    void classify_unavailableBeatsUpstreamError() {
        Throwable error = new RestClientResponseException("x", 500, "Err", HttpHeaders.EMPTY, new byte[0], StandardCharsets.UTF_8);
        error.addSuppressed(new ConnectException("ignored"));
        Throwable wrapper = new RuntimeException("w", new ConnectException("refused"));
        wrapper.addSuppressed(error);
        assertThat(ExceptionClassifier.classify(wrapper)).isEqualTo(ExceptionClassifier.Kind.UPSTREAM_UNAVAILABLE);
    }

    @Test
    @DisplayName("classify: cause found at depth 11 is detected, depth 12 is ignored")
    void classify_depthBoundary() {
        // chain element index 11 is the last one inspected (MAX_DEPTH = 12)
        assertThat(ExceptionClassifier.classify(chainWithRootAtIndex(11)))
                .isEqualTo(ExceptionClassifier.Kind.DATA_CONFLICT);
        assertThat(ExceptionClassifier.classify(chainWithRootAtIndex(12)))
                .isEqualTo(ExceptionClassifier.Kind.OTHER);
    }

    private static Throwable chainWithRootAtIndex(int index) {
        Throwable current = new DataIntegrityViolationException("dup");
        for (int i = 0; i < index; i++) {
            current = new RuntimeException("wrapper " + i, current);
        }
        return current;
    }
}
