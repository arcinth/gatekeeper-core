package com.gatekeeper.config;

import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtAuthenticationFilter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
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
import org.springframework.web.filter.CorsFilter;

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
     * Registered as a plain servlet filter at the highest possible precedence
     * - ahead of Spring Security's own {@code DelegatingFilterProxy} entirely
     * - rather than through {@code HttpSecurity.cors(...)}. That was the first
     * approach tried: it built a correctly-configured {@code CorsFilter}
     * (confirmed via reflection on the built {@code SecurityFilterChain} -
     * right origins, methods, headers, credentials flag), the same class this
     * one wraps, but a live cross-origin preflight from the real frontend
     * still came back {@code 403} regardless of configuration, on this
     * environment. The same failure mode as {@code SecurityHeadersFilter}
     * (see its Javadoc): something about Spring Security's own internal
     * filter dispatch not reliably applying a piece registered through its
     * DSL, even though the configuration going in is correct. This filter -
     * plain, direct, outside Spring Security's chain, the same proven
     * pattern - handles CORS (including short-circuiting preflight requests)
     * before the request ever reaches Spring Security, so an OPTIONS
     * preflight to a protected endpoint is answered without needing
     * authentication at all.
     */
    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilterRegistration() {
        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(corsConfigurationSource()));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * CSP/HSTS/Referrer-Policy/Permissions-Policy (Milestone 10: Security
     * Hardening, Section 2) are set by {@link SecurityHeadersFilter}, a plain
     * servlet filter, not through {@code HttpSecurity.headers(...)} - see
     * that class's Javadoc for why. {@code X-Content-Type-Options},
     * {@code X-Frame-Options}, and {@code Cache-Control} are already covered
     * by Spring Security's own default headers and needed no configuration
     * here at all.
     * <p>
     * No {@code .cors(...)} call here - CORS is fully handled by
     * {@link #corsFilterRegistration()} ahead of this chain; see that
     * bean's Javadoc for why.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
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
