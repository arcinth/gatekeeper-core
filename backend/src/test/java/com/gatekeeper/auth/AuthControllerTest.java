package com.gatekeeper.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.auth.dto.TokenResponse;
import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.security.ratelimit.RateLimitExceededException;
import com.gatekeeper.security.ratelimit.RateLimitService;
import com.gatekeeper.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Covers Milestone 10: Security Hardening's HTTP security headers (set by
 * {@link com.gatekeeper.config.SecurityHeadersFilter}, a plain servlet
 * filter that runs ahead of any controller) and login/refresh rate limiting
 * - both are cross-cutting concerns best proven against a real request
 * through the full filter chain rather than a unit test of either piece in
 * isolation.
 */
@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private UserService userService;

    @MockBean
    private RateLimitService rateLimitService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void login_returnsTheConfiguredSecurityHeaders() throws Exception {
        when(authService.login(any())).thenReturn(new TokenResponse("access", "refresh", "Bearer", 900));

        mockMvc.perform(post("/api/v1/auth/login")
                        .secure(true)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"whatever123\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'"))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("Permissions-Policy", "geolocation=(), camera=(), microphone=()"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                // Spring Security's own default header, applied platform-wide (not a
                // Milestone 10 addition, but exactly the "never cache a token-bearing
                // response" guarantee Section 2 asked for) - see docs/Security-Hardening.md
                // for why no extra code was added on top of this default.
                .andExpect(header().string("Cache-Control", "no-cache, no-store, max-age=0, must-revalidate"));
    }

    @Test
    void login_returns429WhenTheRateLimitIsExceeded() throws Exception {
        doThrow(new RateLimitExceededException("auth.login.ip")).when(rateLimitService).checkLogin(anyString(), anyString());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"whatever123\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("GK-429"));
    }

    @Test
    void refresh_returns429WhenTheRateLimitIsExceeded() throws Exception {
        doThrow(new RateLimitExceededException("auth.refresh.ip")).when(rateLimitService).checkRefresh(anyString());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"x\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("GK-429"));
    }
}
