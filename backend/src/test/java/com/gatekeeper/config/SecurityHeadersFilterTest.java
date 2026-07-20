package com.gatekeeper.config;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

class SecurityHeadersFilterTest {

    private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

    @Test
    void doFilterInternal_setsCspReferrerPolicyAndPermissionsPolicyRegardlessOfScheme() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.isSecure()).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        verify(response).setHeader("Referrer-Policy", "no-referrer");
        verify(response).setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
        verify(chain).doFilter(request, response);
    }

    @Test
    void doFilterInternal_omitsHstsOverPlainHttp() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.isSecure()).thenReturn(false);

        filter.doFilter(request, response, chain);

        verify(response, org.mockito.Mockito.never()).setHeader(org.mockito.ArgumentMatchers.eq("Strict-Transport-Security"), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void doFilterInternal_setsHstsOverHttps() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.isSecure()).thenReturn(true);

        filter.doFilter(request, response, chain);

        verify(response).setHeader("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains");
    }
}
