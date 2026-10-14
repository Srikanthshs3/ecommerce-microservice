package org.praskanth.security.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Turns the identity headers set by the API gateway into a Spring Security {@code Authentication}.
 *
 * It does NOT validate any JWT - the gateway already did, once, and downstream services never see the token.
 * If the headers are absent, malformed, or (when a signing secret is configured) not correctly signed,
 * the request simply stays anonymous and the service's authorisation rules answer 401.
 *
 * Authorities are the raw role names ("ADMIN", "SELLER", ...) exactly as before, so existing
 * {@code hasAuthority('ADMIN')} / {@code @PreAuthorize} expressions are unchanged.
 */
public class GatewayAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuthenticationFilter.class);
    private static final Pattern SAFE_ROLE = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");

    private final HeaderSigner signer; // null => unsigned mode (network isolation is the only protection)
    private final long maxSkewSeconds;

    public GatewayAuthenticationFilter(HeaderSigner signer, long maxSkewSeconds) {
        this.signer = signer;
        this.maxSkewSeconds = maxSkewSeconds;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String rawUserId = request.getHeader(GatewayHeaders.USER_ID);
        if (rawUserId != null) {
            GatewayUser user = resolve(request, rawUserId);
            if (user != null) {
                List<GrantedAuthority> authorities = new ArrayList<>();
                user.roles().forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));

                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(user, null, authorities));
                SecurityContextHolder.setContext(context);
            } else {
                log.warn("Ignoring untrusted identity headers on {} {}", request.getMethod(), request.getRequestURI());
            }
        }
        chain.doFilter(request, response);
    }

    private GatewayUser resolve(HttpServletRequest request, String rawUserId) {
        String email = request.getHeader(GatewayHeaders.USER_EMAIL);
        String rawRoles = request.getHeader(GatewayHeaders.USER_ROLES);

        if (!StringUtils.hasText(email)) {
            return null;
        }
        Long userId;
        try {
            userId = Long.valueOf(rawUserId.trim());
        } catch (NumberFormatException e) {
            return null;
        }

        if (signer != null && !signer.verify(
                rawUserId, email, rawRoles == null ? "" : rawRoles,
                request.getHeader(GatewayHeaders.TIMESTAMP),
                request.getHeader(GatewayHeaders.SIGNATURE),
                Instant.now().getEpochSecond(), maxSkewSeconds)) {
            return null;
        }

        Set<String> roles = new LinkedHashSet<>();
        if (StringUtils.hasText(rawRoles)) {
            for (String role : rawRoles.split(",")) {
                String r = role.trim();
                if (SAFE_ROLE.matcher(r).matches()) {
                    roles.add(r);
                }
            }
        }
        return new GatewayUser(userId, email, roles);
    }
}
