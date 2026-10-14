package org.praskanth.security;

import lombok.RequiredArgsConstructor;
import org.praskanth.security.gateway.GatewayAuthenticationFilter;
import org.praskanth.security.gateway.GatewaySecurityErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Authentication happened at the API gateway; identity arrives as X-User-* headers.
 *
 * /api/v1/coupon/**   -> reached through the gateway by browsers: always needs a logged-in user (+ @PreAuthorize).
 * /internal/v1/coupon/validate and /redeem -> service-to-service calls made while serving a user request
 *                       (cart-service, order-service); the user's identity is forwarded, so they need it too.
 * /internal/v1/coupon/release -> called by order-service from a Kafka consumer thread (order cancelled), where there is
 *                       NO user. Like order-service's own /internal/**, it is safe only because the API gateway
 *                       rejects every /internal/** path: it must never be reachable from outside the cluster network.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;
    private final GatewaySecurityErrorHandler gatewaySecurityErrorHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(gatewaySecurityErrorHandler)
                        .accessDeniedHandler(gatewaySecurityErrorHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/internal/v1/coupon/release").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
