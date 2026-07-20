package com.gatekeeper.config;

import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtAuthenticationFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAccessDeniedHandler jwtAccessDeniedHandler;
    private final UserDetailsService userDetailsService;

    @Value("${gatekeeper.cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /**
     * CSP/HSTS/Referrer-Policy/Permissions-Policy (Milestone 10: Security
     * Hardening, Section 2) are set by {@link SecurityHeadersFilter}, a plain
     * servlet filter, not through {@code HttpSecurity.headers(...)} - see
     * that class's Javadoc for why. {@code X-Content-Type-Options},
     * {@code X-Frame-Options}, and {@code Cache-Control} are already covered
     * by Spring Security's own default headers and needed no configuration
     * here at all.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(jwtAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        // GitHub cannot present our JWT - WebhookSignatureVerifier is this
                        // endpoint's authentication mechanism instead (see GitHubWebhookController).
                        .requestMatchers("/api/v1/github/webhook").permitAll()
                        .requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**").permitAll()
                        // No "/actuator/health" rule here (Milestone 10: Security Hardening
                        // cleanup): Milestone 9 moved every Actuator endpoint onto a separate
                        // management port with its own ManagementSecurityConfig - this port
                        // never serves "/actuator/*" at all (confirmed live: NoResourceFoundException,
                        // handled as a 404 by GlobalExceptionHandler), so a permitAll rule for
                        // it here was dead config describing a trust boundary that no longer
                        // exists. See docs/Observability.md.
                        .anyRequest().authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * "*" narrowed to the specific headers GateKeeper's own frontend actually
     * sends (Milestone 10: Security Hardening) - Authorization for the bearer
     * token, Content-Type for JSON bodies, X-Correlation-Id for the optional
     * client-supplied tracing header CorrelationIdFilter already accepts.
     * Defense-in-depth: allowedOrigins is already a specific list (never a
     * wildcard), so this was not exploitable before, but an explicit allowlist
     * is stronger practice than "*" even where currently safe.
     */
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Correlation-Id"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
