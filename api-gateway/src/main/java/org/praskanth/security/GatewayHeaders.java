package org.praskanth.security;

/**
 * The identity contract between the gateway and every downstream service.
 * Downstream copy lives in common-library: org.praskanth.security.gateway.GatewayHeaders - keep in sync.
 */
public final class GatewayHeaders {

    public static final String USER_ID = "X-User-Id";
    public static final String USER_EMAIL = "X-User-Email";
    public static final String USER_ROLES = "X-User-Roles";
    public static final String TIMESTAMP = "X-Gateway-Timestamp";
    public static final String SIGNATURE = "X-Gateway-Signature";

    /** Headers used by the previous implementation. Still stripped so nobody can smuggle them in. */
    public static final String LEGACY_AUTH_USER = "X-Auth-User";
    public static final String LEGACY_AUTH_ROLES = "X-Auth-Roles";

    private GatewayHeaders() {
    }
}
