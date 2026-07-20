package com.gatekeeper.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.jsonwebtoken.Claims;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Covers Milestone 10: Security Hardening's rewrite of the previously-silent
 * broad {@code catch (Exception)} into specific, logged, metric-emitting
 * rejection paths - see JwtAuthenticationFilter's own Javadoc for why.
 */
class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);
    private final CustomUserDetailsService userDetailsService = mock(CustomUserDetailsService.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(jwtService, userDetailsService, meterRegistryProvider());
    private final FilterChain filterChain = mock(FilterChain.class);

    @SuppressWarnings("unchecked")
    private ObjectProvider<MeterRegistry> meterRegistryProvider() {
        ObjectProvider<MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable(any(Supplier.class))).thenReturn(meterRegistry);
        return provider;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternal_withNoAuthorizationHeader_continuesUnauthenticatedWithoutRecordingAnything() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(meterRegistry.find("gatekeeper.auth.invalid_token").counters()).isEmpty();
    }

    @Test
    void doFilterInternal_withAValidAccessToken_authenticatesAndRecordsNothing() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_TYPE, String.class)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(claims.get(JwtService.CLAIM_EMAIL, String.class)).thenReturn("user@example.com");
        when(jwtService.parseClaims(anyString())).thenReturn(claims);
        when(userDetailsService.loadUserByUsername("user@example.com")).thenReturn(securityUser());

        MockHttpServletRequest request = authorizedRequest("a-valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        verify(filterChain).doFilter(request, response);
        assertThat(meterRegistry.find("gatekeeper.auth.invalid_token").counters()).isEmpty();
    }

    @Test
    void doFilterInternal_withARefreshTokenUsedAsAnAccessToken_rejectsWithWrongTypeReason() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_TYPE, String.class)).thenReturn(JwtService.TOKEN_TYPE_REFRESH);
        when(jwtService.parseClaims(anyString())).thenReturn(claims);

        filter.doFilterInternal(authorizedRequest("a-refresh-token"), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(meterRegistry.counter("gatekeeper.auth.invalid_token", "reason", "wrong_type").count()).isEqualTo(1.0);
    }

    @Test
    void doFilterInternal_withAnInvalidOrExpiredToken_rejectsWithMalformedOrExpiredReason() throws Exception {
        when(jwtService.parseClaims(anyString())).thenThrow(new InvalidTokenException("Token is invalid or expired."));

        filter.doFilterInternal(authorizedRequest("a-bad-token"), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(meterRegistry.counter("gatekeeper.auth.invalid_token", "reason", "malformed_or_expired").count())
                .isEqualTo(1.0);
    }

    @Test
    void doFilterInternal_withATokenForAUserThatNoLongerExists_rejectsWithUserNotFoundReason() throws Exception {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_TYPE, String.class)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(claims.get(JwtService.CLAIM_EMAIL, String.class)).thenReturn("gone@example.com");
        when(jwtService.parseClaims(anyString())).thenReturn(claims);
        when(userDetailsService.loadUserByUsername("gone@example.com"))
                .thenThrow(new UsernameNotFoundException("no such user"));

        filter.doFilterInternal(authorizedRequest("a-token-for-a-deleted-user"), new MockHttpServletResponse(), filterChain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(meterRegistry.counter("gatekeeper.auth.invalid_token", "reason", "user_not_found").count())
                .isEqualTo(1.0);
    }

    private MockHttpServletRequest authorizedRequest(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private SecurityUser securityUser() {
        var organization = com.gatekeeper.organization.Organization.builder().name("Acme").build();
        org.springframework.test.util.ReflectionTestUtils.setField(organization, "id", 1L);
        var user = com.gatekeeper.user.User.builder()
                .email("user@example.com")
                .passwordHash("hash")
                .fullName("Test User")
                .organization(organization)
                .role(com.gatekeeper.role.Role.builder().name("DEVELOPER").build())
                .enabled(true)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 1L);
        return new SecurityUser(user);
    }
}
