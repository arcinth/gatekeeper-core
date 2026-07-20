package com.gatekeeper.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
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
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_MDC_KEY = "userId";
    public static final String ORGANIZATION_ID_MDC_KEY = "organizationId";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

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
        } catch (Exception ex) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
