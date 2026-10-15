package org.praskanth.security;

import lombok.RequiredArgsConstructor;
import org.praskanth.security.gateway.GatewayAuthenticationFilter;
import org.praskanth.security.gateway.GatewaySecurityErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // Identity comes from the headers the API gateway sets after verifying the JWT - no token handling here.
    private final GatewayAuthenticationFilter gatewayAuthenticationFilter;
    private final GatewaySecurityErrorHandler gatewaySecurityErrorHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception{
        httpSecurity.csrf(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(gatewaySecurityErrorHandler)
                        .accessDeniedHandler(gatewaySecurityErrorHandler))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        // authenticated endpoints that live under the public /auth prefix (most specific first)
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/change-password",
                                "/api/v1/auth/logout-all").authenticated()
                        // public: registration, login, token refresh, password reset
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // public: seller sign-up and the shopper-facing store page
                        .requestMatchers(HttpMethod.POST, "/api/v1/sellers/register").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/sellers/*/public").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus").permitAll()
                        .anyRequest().authenticated())
                .sessionManagement(manager-> manager.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(gatewayAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder(){
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }
}
