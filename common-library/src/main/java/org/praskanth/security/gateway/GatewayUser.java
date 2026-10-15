package org.praskanth.security.gateway;

import java.io.Serializable;
import java.security.Principal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The authenticated caller, as vouched for by the API gateway.
 *
 * Use it in controllers with {@code @AuthenticationPrincipal GatewayUser user}. It also implements
 * {@link Principal} (name = email), so existing {@code Principal principal} parameters keep working.
 *
 * It carries ONLY what is in the JWT. Volatile data (KYC status, addresses, ...) is deliberately not here -
 * fetch it from the owning service when you need a fresh value.
 */
public record GatewayUser(Long id, String email, Set<String> roles) implements Principal, Serializable {

    /** Most privileged first. Used to pick ONE role for code that still wants a single role string. */
    private static final List<String> ROLE_PRECEDENCE =
            List.of("ADMIN", "SELLER", "SUPPORT_AGENT", "DELIVERY_PARTNER", "CUSTOMER");

    public GatewayUser {
        roles = roles == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(roles));
    }

    @Override
    public String getName() {
        return email;
    }

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean isAdmin() {
        return hasRole("ADMIN");
    }

    /** Deterministic replacement for the old {@code roles.iterator().next()} (Set order was arbitrary). */
    public String primaryRole() {
        for (String candidate : ROLE_PRECEDENCE) {
            if (roles.contains(candidate)) {
                return candidate;
            }
        }
        return roles.stream().sorted().findFirst().orElse("UNKNOWN");
    }
}
