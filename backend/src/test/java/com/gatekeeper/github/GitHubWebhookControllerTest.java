package com.gatekeeper.github;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.github.exception.InvalidWebhookSignatureException;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * @WebMvcTest does NOT pick up SecurityConfig on its own - it's a plain
 * @Configuration class, and the slice's restricted component scan only
 * auto-includes specific stereotypes (@Controller, @ControllerAdvice, Filter,
 * etc). Without the explicit @Import below, this test would silently fall back
 * to Spring Boot's own auto-configured default security chain (CSRF-enabled,
 * form login) instead of exercising the real permitAll rule this test exists
 * to verify - which is exactly what happened before this import was added:
 * every request came back 403 from Boot's default CsrfFilter, not our config.
 *
 * Mocking JwtAuthenticationFilter itself would break request processing, since
 * a mocked filter never calls filterChain.doFilter(); mocking its leaf
 * dependencies instead lets the real filter run and correctly pass every
 * request here straight through, since none carry an Authorization header.
 */
@WebMvcTest(GitHubWebhookController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class GitHubWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookSignatureVerifier webhookSignatureVerifier;

    @MockBean
    private GitHubEventRouter gitHubEventRouter;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void receiveWebhook_returns200WhenSignatureVerificationSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/github/webhook")
                        .header("X-Hub-Signature-256", "sha256=irrelevant-because-verifier-is-mocked")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"opened\"}"))
                .andExpect(status().isOk());

        verify(webhookSignatureVerifier).verify(any(byte[].class), eq("sha256=irrelevant-because-verifier-is-mocked"));
        verify(gitHubEventRouter).route(eq("pull_request"), any(byte[].class), eq("delivery-1"));
    }

    @Test
    void receiveWebhook_returns401WhenSignatureVerificationFails() throws Exception {
        doThrow(new InvalidWebhookSignatureException("Webhook signature does not match the configured secret."))
                .when(webhookSignatureVerifier).verify(any(byte[].class), any());

        mockMvc.perform(post("/api/v1/github/webhook")
                        .header("X-Hub-Signature-256", "sha256=wrong")
                        .header("X-GitHub-Event", "pull_request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("GK-401"));

        verify(gitHubEventRouter, never()).route(any(), any(byte[].class), any());
    }

    @Test
    void receiveWebhook_doesNotRequireJwtAuthentication() throws Exception {
        // No Authorization header at all - GitHub has no way to send our JWT, so this
        // endpoint must be reachable without one (trust comes from the signature instead).
        mockMvc.perform(post("/api/v1/github/webhook")
                        .header("X-Hub-Signature-256", "sha256=irrelevant-because-verifier-is-mocked")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }
}
