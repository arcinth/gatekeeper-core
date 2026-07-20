package com.gatekeeper.security;

import io.jsonwebtoken.Claims;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedCredentialsNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * On successful authentication, also adds {@code userId}/{@code organizationId}
 * to SLF4J's MDC (Milestone 9: Observability) - safe to add without matching
 * local cleanup, since {@link com.gatekeeper.config.CorrelationIdFilter}
 * (which wraps this filter, running first) owns the whole request's MDC
 * lifecycle and clears every key, not just its own, once the response is
 * complete.
 * <p>
 * A rejected token never fails the request outright - the filter chain
 * continues unauthenticated, and whatever's downstream (an authenticated
 * endpoint's own access-control check) decides whether that matters. What
 * changed in Milestone 10: Security Hardening is that a rejection is no
 * longer silent - {@code gatekeeper.auth.invalid_token{reason}} is
 * incremented and an INFO line is logged (INFO, not WARN - an expired token
 * on a routine API call is the ordinary case, not an anomaly), closing a real
 * observability gap: previously, nothing about an invalid/expired/tampered
 * token on a normal API call was visible in logs or metrics at all. The
 * broad {@code catch (Exception)} this replaces is narrowed to the specific
 * exceptions this block can actually throw, so a genuine bug elsewhere in
 * this method is no longer swallowed alongside expected authentication
 * failures.
 * <p>
 * Takes an {@link ObjectProvider} rather than a plain {@code MeterRegistry}
 * dependency for the same reason as {@code GlobalExceptionHandler} (Milestone
 * 9) - this filter, unlike most beans, is a real {@code @Component} that
 * every {@code @WebMvcTest} slice constructs (it's a servlet {@code Filter}
 * Spring Boot's test slicing always includes), and those slices don't
 * autoconfigure a {@code MeterRegistry} bean by default.
 */
@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_MDC_KEY = "userId";
    public static final String ORGANIZATION_ID_MDC_KEY = "organizationId";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String METRIC_NAME = "gatekeeper.auth.invalid_token";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final MeterRegistry meterRegistry;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService,
            ObjectProvider<MeterRegistry> meterRegistry) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
        this.meterRegistry = meterRegistry.getIfAvailable(SimpleMeterRegistry::new);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        try {
            Claims claims = jwtService.parseClaims(token);
            if (!JwtService.TOKEN_TYPE_ACCESS.equals(claims.get(JwtService.CLAIM_TYPE, String.class))) {
                throw new PreAuthenticatedCredentialsNotFoundException("Token is not an access token.");
            }

            String email = claims.get(JwtService.CLAIM_EMAIL, String.class);
            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            var authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            if (userDetails instanceof SecurityUser securityUser) {
                MDC.put(USER_ID_MDC_KEY, String.valueOf(securityUser.getId()));
                MDC.put(ORGANIZATION_ID_MDC_KEY, String.valueOf(securityUser.getOrganizationId()));
            }
        } catch (InvalidTokenException ex) {
            // JwtService.parseClaims wraps every JJWT-level failure (malformed,
            // expired, tampered signature, wrong issuer) into this one exception type.
            SecurityContextHolder.clearContext();
            recordInvalidToken("malformed_or_expired");
        } catch (PreAuthenticatedCredentialsNotFoundException ex) {
            SecurityContextHolder.clearContext();
            recordInvalidToken("wrong_type");
        } catch (UsernameNotFoundException ex) {
            SecurityContextHolder.clearContext();
            recordInvalidToken("user_not_found");
        }

        filterChain.doFilter(request, response);
    }

    private void recordInvalidToken(String reason) {
        log.info("Rejected an invalid access token (reason={}).", reason);
        meterRegistry.counter(METRIC_NAME, "reason", reason).increment();
    }
}
