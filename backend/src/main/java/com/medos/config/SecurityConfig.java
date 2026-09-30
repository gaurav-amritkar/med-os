package com.medos.config;

import com.medos.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final IdempotencyFilter idempotencyFilter;

    @Value("${medos.cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * Whether unauthenticated callers may register a new tenant.
     *
     * <p>Defaults to false, per ADR-0003. The registration handler creates a
     * tenant, a user and an {@code admin} membership from the request body, so
     * leaving it {@code permitAll} let any anonymous caller provision a
     * hospital with full administrative rights — unlimited, unaudited and
     * unrated-limited.
     *
     * <p>With this off, the path falls through to {@code anyRequest()
     * .authenticated()} and even a tenant admin is refused. Only a platform
     * super-admin should be able to register a tenant, and that role does not
     * exist yet, so "no" is currently the correct answer for every caller.
     */
    @Value("${medos.onboarding.public-signup:false}")
    private boolean publicOnboardingSignup;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write("""
                        {"status":401,"error":"Unauthorized","code":"UNAUTHENTICATED","message":"Authentication required","path":"%s","timestamp":"%s"}
                        """.formatted(request.getRequestURI(), java.time.LocalDateTime.now()));
                })
            )
            .authorizeHttpRequests(auth -> {
                if (publicOnboardingSignup) {
                    // Explicitly opted in. Reachable only because an operator set
                    // medos.onboarding.public-signup=true.
                    auth.requestMatchers("/api/v1/auth/**", "/api/v1/onboarding/register").permitAll();
                } else {
                    // Self-serve registration is closed: /onboarding/register is
                    // deliberately absent, so it requires authentication like any
                    // other endpoint.
                    auth.requestMatchers("/api/v1/auth/**").permitAll();
                }
                auth
                // Actuator: only health/info public; everything else under /manage requires ADMIN.
                .requestMatchers("/manage/health", "/manage/info").permitAll()
                .requestMatchers("/manage/**").hasRole("ADMIN")
                // OpenAPI docs: swagger-ui and api-docs require ADMIN role.
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**")
                .hasRole("ADMIN")
                // WebSocket handshake is open; token is enforced at the STOMP CONNECT layer.
                .requestMatchers("/ws/**").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated();
            })
            // Register the JWT filter first so it can serve as a positioned anchor;
            // idempotency then runs after authentication (replay check is post-auth).
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(idempotencyFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Idempotency-Key",
                "X-Requested-With", "Accept", "Origin", "X-Tenant-Id"
        ));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg) throws Exception {
        return cfg.getAuthenticationManager();
    }
}
