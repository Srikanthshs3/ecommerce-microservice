package org.praskanth.config;

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
 * Authentication happened at the API gateway; identity arrives as X-User-* headers (GatewayAuthenticationFilter).
 * Every endpoint except actuator requires an authenticated caller; finer rules use @PreAuthorize.
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
                .csrf(AbstractHttpConfigurer::disable) // stateless, no browser session/cookies
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(gatewaySecurityErrorHandler)
                        .accessDeniedHandler(gatewaySecurityErrorHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/api/v1/payment/webhooks/**").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
