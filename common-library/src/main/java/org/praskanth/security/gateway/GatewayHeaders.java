package org.praskanth.security.gateway;

import java.util.List;

/**
 * The identity contract between the API gateway and every downstream service.
 * The gateway has its own copy (org.praskanth.security.GatewayHeaders) - keep both in sync.
 */
public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLES = "X-User-Roles";
    public static final String TIMESTAMP = "X-Gateway-Timestamp";
    public static final String SIGNATURE = "X-Gateway-Signature";

    /** Copied verbatim onto service-to-service calls made while handling an authenticated request. */
    public static final List<String> PROPAGATED = List.of(USER_ID, USER_EMAIL, USER_ROLES, TIMESTAMP, SIGNATURE);

    private GatewayHeaders() {
    }
}
