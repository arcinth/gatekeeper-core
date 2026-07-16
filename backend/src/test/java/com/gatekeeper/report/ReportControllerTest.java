package com.gatekeeper.report;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.analysisrun.dto.PullRequestReference;
import com.gatekeeper.analysisrun.dto.RepositoryReference;
import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.pullrequest.PullRequestStatus;
import com.gatekeeper.report.dto.ReportDetailResponse;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.verdictengine.VerdictOutcome;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class ReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportQueryService reportQueryService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void findByAnalysisRunId_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/analysis-runs/1/report")).andExpect(status().isUnauthorized());
    }

    @Test
    void findByAnalysisRunId_returnsTheComposedReportWhenAuthenticated() throws Exception {
        authenticateAs("dev@example.com");
        ReportDetailResponse report = new ReportDetailResponse(
                3L, 1L, AnalysisRunStatus.COMPLETED, AnalysisRunTriggerReason.OPENED, "cafebabe",
                Instant.now(), Instant.now(),
                new RepositoryReference(9L, "org/core"),
                new PullRequestReference(7, "Add feature", "octocat", "feature", "main", "cafebabe", PullRequestStatus.OPEN),
                List.of(), List.of(), AiReviewStatus.DISABLED, List.of(),
                VerdictOutcome.BLOCKED, List.of(), List.of(), Instant.now());
        when(reportQueryService.findByAnalysisRunIdOrThrow(1L)).thenReturn(report);

        mockMvc.perform(get("/api/v1/analysis-runs/1/report").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisRunId").value(1))
                .andExpect(jsonPath("$.data.repository.fullName").value("org/core"))
                .andExpect(jsonPath("$.data.pullRequest.number").value(7))
                .andExpect(jsonPath("$.data.verdictOutcome").value("BLOCKED"))
                .andExpect(jsonPath("$.data.aiReviewStatus").value("DISABLED"));
    }

    @Test
    void findByAnalysisRunId_returns404WhenNoReportHasBeenPublishedYet() throws Exception {
        authenticateAs("dev@example.com");
        when(reportQueryService.findByAnalysisRunIdOrThrow(404L))
                .thenThrow(new ResourceNotFoundException("Engineering report not found for analysis run: 404"));

        mockMvc.perform(get("/api/v1/analysis-runs/404/report").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }

    @Test
    void findByAnalysisRunId_returns400WhenTheIdIsNotANumber() throws Exception {
        authenticateAs("dev@example.com");

        mockMvc.perform(get("/api/v1/analysis-runs/not-a-number/report").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GK-400"));
    }

    private void authenticateAs(String email) {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_TYPE, String.class)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(claims.get(JwtService.CLAIM_EMAIL, String.class)).thenReturn(email);
        when(jwtService.parseClaims(anyString())).thenReturn(claims);
        UserDetails userDetails = User.withUsername(email).password("x").authorities("ROLE_DEVELOPER").build();
        when(customUserDetailsService.loadUserByUsername(eq(email))).thenReturn(userDetails);
    }
}
