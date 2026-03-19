package com.broadnet.billing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security configuration for the billing service.
 *
 * Architecture Plan: This service sits BEHIND the API gateway.
 * - JWT validation is done by the AUTH SERVICE — NOT here.
 * - This service trusts headers set by the gateway after JWT validation.
 * - No JWT parsing, no JwtFilter, no UserDetailsService needed.
 *
 * Security model:
 * ┌────────────────────────────────────────────────┐
 * │ Public  — no auth needed                       │
 * │   POST /webhooks/stripe   (signature verified) │
 * │   GET  /actuator/health                        │
 * ├────────────────────────────────────────────────┤
 * │ Authenticated — X-Company-Id required          │
 * │   /api/billing/**                              │
 * │   enforced by CompanyIdFilter                  │
 * ├────────────────────────────────────────────────┤
 * │ Admin — X-User-Role: ROLE_ADMIN required       │
 * │   /api/admin/**                                │
 * │   enforced by AdminRoleFilter                  │
 * └────────────────────────────────────────────────┘
 *
 * CSRF disabled — stateless REST API, no browser sessions.
 * Sessions are stateless — no HttpSession created.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CompanyIdFilter companyIdFilter;
    private final AdminRoleFilter adminRoleFilter;

    public SecurityConfig(CompanyIdFilter companyIdFilter, AdminRoleFilter adminRoleFilter) {
        this.companyIdFilter = companyIdFilter;
        this.adminRoleFilter = adminRoleFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // No CSRF — stateless REST API
                .csrf(AbstractHttpConfigurer::disable)

                // Stateless — no HttpSession
                .sessionManagement(sm ->
                        sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CORS
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Authorization rules (coarse-grained — fine-grained done in filters)
                .authorizeHttpRequests(auth -> auth
                        // Stripe webhooks — public, signature verified inside service
                        .requestMatchers(HttpMethod.POST, "/webhooks/stripe").permitAll()

                        // Health / readiness probes
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // Everything else — require that filters passed
                        // (CompanyIdFilter enforces X-Company-Id; AdminRoleFilter enforces role)
                        .anyRequest().permitAll()
                )

                // Our filters run before Spring's default auth filter
                // Order: CompanyIdFilter → AdminRoleFilter → controller
                .addFilterBefore(companyIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(adminRoleFilter, CompanyIdFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // Internal service — allow requests from gateway/frontend origins
        config.setAllowedOriginPatterns(List.of(
                "https://*.broadnet.ai",
                "http://localhost:*"    // local dev
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Content-Type",
                "X-Company-Id",
                "X-User-Id",
                "X-User-Role",
                "Stripe-Signature"
        ));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}