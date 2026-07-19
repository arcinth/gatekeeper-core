package com.gatekeeper.aireviewrun;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.aireviewrun.dto.AIReviewRunSummaryResponse;
import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AIReviewRunController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class AIReviewRunControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AIReviewRunQueryService aiReviewRunQueryService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void findAll_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/ai-review-runs")).andExpect(status().isUnauthorized());
    }

    @Test
    void findAll_returnsThePagedResultWhenAuthenticated() throws Exception {
        authenticateAs("dev@example.com");
        AIReviewRunSummaryResponse run = new AIReviewRunSummaryResponse(1L, 9L, "org/core", 21, "sha",
                AIReviewRunStatus.COMPLETED, "anthropic-claude", "claude-opus-4-6", "v1", Instant.now(), Instant.now(), 2L);
        Page<AIReviewRunSummaryResponse> page = new PageImpl<>(List.of(run), PageRequest.of(0, 20), 1);
        when(aiReviewRunQueryService.findPage(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/ai-review-runs?analysisRunId=9").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].provider").value("anthropic-claude"))
                .andExpect(jsonPath("$.data.content[0].repositoryFullName").value("org/core"));
    }

    @Test
    void findById_returns404WhenTheServiceThrowsResourceNotFound() throws Exception {
        authenticateAs("dev@example.com");
        when(aiReviewRunQueryService.findDetailByIdOrThrow(404L))
                .thenThrow(new ResourceNotFoundException("AIReviewRun not found with id: 404"));

        mockMvc.perform(get("/api/v1/ai-review-runs/404").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }

    @Test
    void findAll_returns400WhenTheStatusFilterIsAnInvalidEnumValue() throws Exception {
        authenticateAs("dev@example.com");

        mockMvc.perform(get("/api/v1/ai-review-runs?status=NOT_A_STATUS").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GK-400"));
    }

    private void authenticateAs(String email) {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_TYPE, String.class)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(claims.get(JwtService.CLAIM_EMAIL, String.class)).thenReturn(email);
        when(jwtService.parseClaims(anyString())).thenReturn(claims);
        UserDetails userDetails = User.withUsername(email).password("x").authorities("ROLE_DEVELOPER", "WORKSPACE_READ").build();
        when(customUserDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
    }
}
