package com.gatekeeper.dashboard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
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

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DashboardAggregationService dashboardAggregationService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void status_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
    }

    @Test
    void status_returnsTheHardcodedStatusWhenAuthenticated() throws Exception {
        authenticateAs("dev@example.com");

        mockMvc.perform(get("/api/v1/dashboard").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("running"));
    }

    @Test
    void overview_returnsTheAggregatedCountsWhenAuthenticated() throws Exception {
        authenticateAs("dev@example.com");
        DashboardOverviewResponse overview = new DashboardOverviewResponse(
                30, 5L, 12L,
                Map.of(AnalysisRunStatus.COMPLETED, 10L, AnalysisRunStatus.FAILED, 2L),
                7L,
                Map.of(PolicySeverity.LOW, 5L, PolicySeverity.MEDIUM, 2L),
                Map.of(PolicyCategory.MAINTAINABILITY, 5L, PolicyCategory.CODE_QUALITY, 2L));
        when(dashboardAggregationService.getOverview(any())).thenReturn(overview);

        mockMvc.perform(get("/api/v1/dashboard/overview").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAnalysisRuns").value(12))
                .andExpect(jsonPath("$.data.totalFindings").value(7))
                .andExpect(jsonPath("$.data.windowDays").value(30));
    }

    @Test
    void overview_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/overview")).andExpect(status().isUnauthorized());
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
