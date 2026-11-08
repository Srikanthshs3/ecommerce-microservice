package org.praskanth.exception;

import java.util.Set;

/**
 * Decides what an otherwise-unhandled exception really means, WITHOUT importing the libraries involved
 * (JPA/Mongo, Resilience4j, Spring Cloud LoadBalancer ...). common-library is scanned into every service,
 * and not every service has every library, so classes are matched by name along the cause chain.
 *
 * Pure Java on purpose: it is unit-tested without Spring.
 */
final class ExceptionClassifier {

    enum Kind {
        /** Unique/foreign-key/constraint violation -> 409 */
        DATA_CONFLICT,
        /** Optimistic/pessimistic locking failure -> 409, retryable */
        CONCURRENT_UPDATE,
        /** Dependency down, timed out, no instance registered, circuit open -> 503 */
        UPSTREAM_UNAVAILABLE,
        /** Our own database / Redis cannot be reached or is too slow -> 503 */
        DATABASE_UNAVAILABLE,
        /** Dependency answered with an error status -> 502 */
        UPSTREAM_ERROR,
        /** Genuinely unexpected -> 500 */
        OTHER
    }

    private static final Set<String> DATA_CONFLICT = Set.of(
            "org.springframework.dao.DataIntegrityViolationException");

    private static final Set<String> CONCURRENT_UPDATE = Set.of(
            "org.springframework.dao.OptimisticLockingFailureException",
            "org.springframework.dao.PessimisticLockingFailureException",
            "jakarta.persistence.OptimisticLockException");

    private static final Set<String> UPSTREAM_UNAVAILABLE = Set.of(
            "org.springframework.web.client.ResourceAccessException",
            "io.github.resilience4j.circuitbreaker.CallNotPermittedException",
            "io.github.resilience4j.bulkhead.BulkheadFullException",
            "java.net.ConnectException",
            "java.net.SocketTimeoutException",
            "java.net.UnknownHostException");

    private static final Set<String> DATABASE_UNAVAILABLE = Set.of(
            "org.springframework.dao.DataAccessResourceFailureException",
            "org.springframework.dao.QueryTimeoutException",
            "org.springframework.jdbc.CannotGetJdbcConnectionException",
            "org.springframework.transaction.CannotCreateTransactionException",
            "org.hibernate.exception.JDBCConnectionException",
            "java.sql.SQLTransientConnectionException",
            "com.zaxxer.hikari.pool.HikariPool$PoolInitializationException",
            "com.mongodb.MongoTimeoutException",
            "com.mongodb.MongoSocketException",
            "org.springframework.data.redis.RedisConnectionFailureException",
            "io.lettuce.core.RedisConnectionException");

    private static final Set<String> UPSTREAM_ERROR = Set.of(
            "org.springframework.web.client.RestClientResponseException");

    /** Thrown by Spring Cloud LoadBalancer's RestTemplate/RestClient interceptor when Eureka has no instance. */
    private static final String NO_INSTANCES_PREFIX = "No instances available for";

    private static final int MAX_DEPTH = 12;

    private ExceptionClassifier() {
    }

    static Kind classify(Throwable error) {
        boolean dataConflict = false, concurrent = false, unavailable = false, upstreamError = false, database = false;

        int depth = 0;
        for (Throwable t = error; t != null && depth < MAX_DEPTH; t = t.getCause() == t ? null : t.getCause(), depth++) {
            if (t.getMessage() != null && t.getMessage().startsWith(NO_INSTANCES_PREFIX)) {
                unavailable = true;
            }
            for (Class<?> c = t.getClass(); c != null; c = c.getSuperclass()) {
                String name = c.getName();
                dataConflict |= DATA_CONFLICT.contains(name);
                concurrent |= CONCURRENT_UPDATE.contains(name);
                unavailable |= UPSTREAM_UNAVAILABLE.contains(name);
                database |= DATABASE_UNAVAILABLE.contains(name);
                upstreamError |= UPSTREAM_ERROR.contains(name);
            }
        }

        if (dataConflict) return Kind.DATA_CONFLICT;
        if (concurrent) return Kind.CONCURRENT_UPDATE;
        if (database) return Kind.DATABASE_UNAVAILABLE;
        if (unavailable) return Kind.UPSTREAM_UNAVAILABLE;
        if (upstreamError) return Kind.UPSTREAM_ERROR;
        return Kind.OTHER;
    }
}
