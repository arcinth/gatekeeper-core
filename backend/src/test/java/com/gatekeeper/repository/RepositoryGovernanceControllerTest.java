package com.gatekeeper.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.repository.dto.RepositoryGovernanceResponse;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.verdictengine.VerdictOutcome;
import io.jsonwebtoken.Claims;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RepositoryGovernanceController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class RepositoryGovernanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RepositoryGovernanceService repositoryGovernanceService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void getGovernanceSummary_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/repositories/9/governance")).andExpect(status().isUnauthorized());
    }

    @Test
    void getGovernanceSummary_returnsTheAggregatedCountsWhenAuthenticated() throws Exception {
        authenticateAs("dev@example.com");
        RepositoryGovernanceResponse summary = new RepositoryGovernanceResponse(
                9L, "org/core", 30,
                6L, Map.of(AnalysisRunStatus.COMPLETED, 5L, AnalysisRunStatus.FAILED, 1L),
                3L, Map.of(PolicySeverity.LOW, 3L), Map.of(PolicyCategory.MAINTAINABILITY, 3L),
                1L, Map.of(), Map.of(),
                2L, Map.of(), Map.of(),
                5L, Map.of(VerdictOutcome.APPROVED, 4L, VerdictOutcome.BLOCKED, 1L),
                3L, Map.of());
        when(repositoryGovernanceService.getGovernanceSummary(9L, null)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/repositories/9/governance").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repositoryId").value(9))
                .andExpect(jsonPath("$.data.repositoryFullName").value("org/core"))
                .andExpect(jsonPath("$.data.windowDays").value(30))
                .andExpect(jsonPath("$.data.totalAnalysisRuns").value(6))
                .andExpect(jsonPath("$.data.totalVerdicts").value(5))
                .andExpect(jsonPath("$.data.verdictsByOutcome.BLOCKED").value(1));
    }

    @Test
    void getGovernanceSummary_passesTheWindowDaysQueryParamThrough() throws Exception {
        authenticateAs("dev@example.com");
        RepositoryGovernanceResponse summary = new RepositoryGovernanceResponse(
                9L, "org/core", 7,
                0L, Map.of(), 0L, Map.of(), Map.of(), 0L, Map.of(), Map.of(),
                0L, Map.of(), Map.of(), 0L, Map.of(), 0L, Map.of());
        when(repositoryGovernanceService.getGovernanceSummary(9L, 7)).thenReturn(summary);

        mockMvc.perform(get("/api/v1/repositories/9/governance?windowDays=7").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.windowDays").value(7));
    }

    @Test
    void getGovernanceSummary_returns404ForAnUnknownRepository() throws Exception {
        authenticateAs("dev@example.com");
        when(repositoryGovernanceService.getGovernanceSummary(any(), any()))
                .thenThrow(new ResourceNotFoundException("Repository not found with id: 404"));

        mockMvc.perform(get("/api/v1/repositories/404/governance").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }

    @Test
    void getGovernanceSummary_returns400WhenTheIdIsNotANumber() throws Exception {
        authenticateAs("dev@example.com");

        mockMvc.perform(get("/api/v1/repositories/not-a-number/governance").header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GK-400"));
    }

    @Test
    void getGovernanceSummary_returns400WhenWindowDaysIsNotANumber() throws Exception {
        authenticateAs("dev@example.com");

        mockMvc.perform(get("/api/v1/repositories/9/governance?windowDays=not-a-number")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GK-400"));
    }

    private void authenticateAs(String email) {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_TYPE, String.class)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(claims.get(JwtService.CLAIM_EMAIL, String.class)).thenReturn(email);
        when(jwtService.parseClaims(anyString())).thenReturn(claims);
        UserDetails userDetails = User.withUsername(email).password("x").authorities("ROLE_DEVELOPER").build();
        when(customUserDetailsService.loadUserByUsername(email)).thenReturn(userDetails);
    }
}
