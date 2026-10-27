package org.praskanth.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.praskanth.config.ApiGatewayProperties;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The gateway is the only door into the system, so this filter is tested hard:
 * public vs protected paths, token problems, header spoofing, internal paths, path tricks and signing.
 */
@ExtendWith(MockitoExtension.class)
class AuthenticationFilterTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Mock JwtUtils jwtUtils;
    @Mock GatewayFilterChain chain;

    ApiGatewayProperties props;
    AuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        props = new ApiGatewayProperties();
        props.setPublicPaths(List.of("/api/v1/auth/**", "GET /api/v1/product/**"));
        filter = new AuthenticationFilter(jwtUtils, props, new ObjectMapper(), "");
    }

    private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> b) {
        return MockServerWebExchange.from(b.build());
    }

    private ServerWebExchange passedOn() {
        ArgumentCaptor<ServerWebExchange> c = ArgumentCaptor.forClass(ServerWebExchange.class);
        verify(chain).filter(c.capture());
        return c.getValue();
    }

    private void chainOk() {
        when(chain.filter(any(ServerWebExchange.class))).thenReturn(Mono.empty());
    }

    @Test
    void protectedPathWithoutTokenIs401MissingToken() {
        MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/v1/cart"));

        filter.filter(ex, chain).block();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getResponse().getHeaders().getFirst(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(ex.getResponse().getBodyAsString().block()).contains("MISSING_TOKEN").contains("\"success\":false");
        verify(chain, never()).filter(any());
    }

    @Test
    void protectedPathWithBadTokenIs401InvalidToken() {
        when(jwtUtils.authenticate("bad")).thenReturn(Optional.empty());
        MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/v1/cart").header("Authorization", "Bearer bad"));

        filter.filter(ex, chain).block();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ex.getResponse().getBodyAsString().block()).contains("INVALID_TOKEN");
    }

    @Test
    void validTokenForwardsIdentityHeadersAndDropsTheRawToken() {
        chainOk();
        when(jwtUtils.authenticate("good")).thenReturn(Optional.of(new JwtUtils.Identity(7L, "a@b.com", List.of("CUSTOMER", "SELLER"))));
        MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/v1/cart").header("Authorization", "Bearer good"));

        filter.filter(ex, chain).block();

        HttpHeaders h = passedOn().getRequest().getHeaders();
        assertThat(h.getFirst(GatewayHeaders.USER_ID)).isEqualTo("7");
        assertThat(h.getFirst(GatewayHeaders.USER_EMAIL)).isEqualTo("a@b.com");
        assertThat(h.getFirst(GatewayHeaders.USER_ROLES)).isEqualTo("CUSTOMER,SELLER");
        assertThat(h.containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
        assertThat(h.containsKey(GatewayHeaders.SIGNATURE)).isFalse();   // no signing secret configured
        assertThat(h.getFirst("X-Correlation-Id")).isNotBlank();
    }

    @Test
    void headersSentByTheClientAreAlwaysDiscarded() {
        chainOk();
        when(jwtUtils.authenticate("good")).thenReturn(Optional.of(new JwtUtils.Identity(7L, "a@b.com", List.of("CUSTOMER"))));
        MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/v1/cart")
                .header("Authorization", "Bearer good")
                .header(GatewayHeaders.USER_ID, "1")
                .header(GatewayHeaders.USER_ROLES, "ADMIN")
                .header(GatewayHeaders.LEGACY_AUTH_ROLES, "ADMIN"));

        filter.filter(ex, chain).block();

        HttpHeaders h = passedOn().getRequest().getHeaders();
        assertThat(h.get(GatewayHeaders.USER_ID)).containsExactly("7");
        assertThat(h.get(GatewayHeaders.USER_ROLES)).containsExactly("CUSTOMER");
        assertThat(h.containsKey(GatewayHeaders.LEGACY_AUTH_ROLES)).isFalse();
    }

    @Test
    void spoofedIdentityHeadersOnAPublicPathWithoutTokenAreStripped() {
        chainOk();
        MockServerWebExchange ex = exchange(MockServerHttpRequest.post("/api/v1/auth/login")
                .header(GatewayHeaders.USER_ID, "1").header(GatewayHeaders.USER_ROLES, "ADMIN"));

        filter.filter(ex, chain).block();

        HttpHeaders h = passedOn().getRequest().getHeaders();
        assertThat(h.containsKey(GatewayHeaders.USER_ID)).isFalse();
        assertThat(h.containsKey(GatewayHeaders.USER_ROLES)).isFalse();
    }

    @Test
    void publicPathPassesWithoutToken() {
        chainOk();
        filter.filter(exchange(MockServerHttpRequest.post("/api/v1/auth/login")), chain).block();
        verify(chain).filter(any());
    }

    @Test
    void invalidTokenOnAPublicPathIsTreatedAsAnonymous() {
        chainOk();
        when(jwtUtils.authenticate("expired")).thenReturn(Optional.empty());
        MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/v1/product/list").header("Authorization", "Bearer expired"));

        filter.filter(ex, chain).block();

        assertThat(passedOn().getRequest().getHeaders().containsKey(GatewayHeaders.USER_ID)).isFalse();
    }

    @Test
    void methodSpecificPublicPathOnlyOpensThatMethod() {
        chainOk();
        filter.filter(exchange(MockServerHttpRequest.get("/api/v1/product/1")), chain).block();
        verify(chain).filter(any());

        MockServerWebExchange post = exchange(MockServerHttpRequest.post("/api/v1/product/create"));
        filter.filter(post, chain).block();
        assertThat(post.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void internalPathsAreNeverReachableFromOutside() {
        MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/internal/v1/orders/5"));

        filter.filter(ex, chain).block();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        verify(chain, never()).filter(any());
        verify(jwtUtils, never()).authenticate(anyString());
    }

    @Test
    void dotSegmentsCannotDisguiseAProtectedPathAsPublic() {
        MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/v1/auth/../cart"));

        filter.filter(ex, chain).block();

        assertThat(ex.getResponse().getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verify(chain, never()).filter(any());
    }

    @Test
    void bearerSchemeIsCaseInsensitiveButEmptyTokenIsMissing() {
        chainOk();
        when(jwtUtils.authenticate("t")).thenReturn(Optional.of(new JwtUtils.Identity(1L, "a@b.com", List.of("ADMIN"))));
        filter.filter(exchange(MockServerHttpRequest.get("/api/v1/cart").header("Authorization", "bearer t")), chain).block();
        verify(chain).filter(any());

        MockServerWebExchange empty = exchange(MockServerHttpRequest.get("/api/v1/cart").header("Authorization", "Bearer   "));
        filter.filter(empty, chain).block();
        assertThat(empty.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void existingCorrelationIdIsKept() {
        chainOk();
        filter.filter(exchange(MockServerHttpRequest.post("/api/v1/auth/login").header("X-Correlation-Id", "abc-123")), chain).block();
        assertThat(passedOn().getRequest().getHeaders().getFirst("X-Correlation-Id")).isEqualTo("abc-123");
    }

    @Test
    void corsPreflightIsPassedThroughWithoutAuthentication() {
        chainOk();
        MockServerWebExchange ex = exchange(MockServerHttpRequest.options("/api/v1/cart")
                .header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "GET"));

        filter.filter(ex, chain).block();

        verify(chain).filter(any());
        verify(jwtUtils, never()).authenticate(anyString());
    }

    @Test
    void whenASigningSecretIsConfiguredTheIdentityIsSignedAndVerifiable() {
        AuthenticationFilter signing = new AuthenticationFilter(jwtUtils, props, new ObjectMapper(), SECRET);
        chainOk();
        when(jwtUtils.authenticate("good")).thenReturn(Optional.of(new JwtUtils.Identity(7L, "a@b.com", List.of("CUSTOMER"))));
        MockServerWebExchange ex = exchange(MockServerHttpRequest.get("/api/v1/cart").header("Authorization", "Bearer good"));

        signing.filter(ex, chain).block();

        HttpHeaders h = passedOn().getRequest().getHeaders();
        String ts = h.getFirst(GatewayHeaders.TIMESTAMP);
        String sig = h.getFirst(GatewayHeaders.SIGNATURE);
        assertThat(ts).isNotBlank();
        assertThat(new HeaderSigner(SECRET).verify("7", "a@b.com", "CUSTOMER", ts, sig, Long.parseLong(ts), 5)).isTrue();
    }

    @Test
    void filterRunsVeryEarly() {
        assertThat(filter.getOrder()).isLessThan(0);
    }
}
