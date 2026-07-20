package com.gatekeeper.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Sets the security response headers appropriate for a stateless JSON API
 * with a separately-hosted frontend (Milestone 10: Security Hardening,
 * Section 2). {@code X-Content-Type-Options}, {@code X-Frame-Options}, and
 * {@code Cache-Control} are already covered by Spring Security's own default
 * headers and are deliberately left alone here.
 * <p>
 * Implemented as a plain servlet filter - mirroring {@link CorrelationIdFilter}'s
 * proven-working pattern of a direct {@code response.setHeader(...)} call
 * before {@code filterChain.doFilter(...)} - rather than through
 * {@code HttpSecurity.headers(...)}'s {@code HeaderWriterFilter} mechanism.
 * Both approaches were implemented and verified against a real running
 * instance (not just {@code @WebMvcTest}, which does not reliably reproduce
 * every difference in how a real embedded Tomcat response commits headers):
 * the {@code HeadersConfigurer}-based approach populated its writer list
 * correctly (confirmed via reflection on the built {@code SecurityFilterChain})
 * but the added headers never actually reached the wire on a live request,
 * while this plain-filter approach does. Deliberately excludes
 * Cross-Origin-Opener-Policy/Cross-Origin-Embedder-Policy (GateKeeper never
 * opens/embeds cross-origin windows or resources, so neither header defends
 * against a threat this platform has) and leaves CSP's frontend-facing
 * policy to the frontend itself, since the backend never serves HTML except
 * Swagger UI (local/dev only).
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP_VALUE = "default-src 'none'; frame-ancestors 'none'";
    private static final String REFERRER_POLICY_VALUE = "no-referrer";
    private static final String PERMISSIONS_POLICY_VALUE = "geolocation=(), camera=(), microphone=()";
    private static final String HSTS_VALUE = "max-age=31536000 ; includeSubDomains";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        response.setHeader("Content-Security-Policy", CSP_VALUE);
        response.setHeader("Referrer-Policy", REFERRER_POLICY_VALUE);
        response.setHeader("Permissions-Policy", PERMISSIONS_POLICY_VALUE);
        // HSTS only means anything - and only makes sense to send - over an actual HTTPS
        // connection; sending it over plain HTTP would be meaningless and could mislead
        // an operator into thinking transport security is enforced when it isn't.
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", HSTS_VALUE);
        }

        filterChain.doFilter(request, response);
    }
}
