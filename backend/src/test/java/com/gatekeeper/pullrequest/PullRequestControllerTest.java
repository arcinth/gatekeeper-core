package com.gatekeeper.pullrequest;

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
import com.gatekeeper.pullrequest.dto.AnalysisRunReference;
import com.gatekeeper.pullrequest.dto.PullRequestDetailResponse;
import com.gatekeeper.pullrequest.dto.PullRequestSummaryResponse;
import com.gatekeeper.pullrequest.dto.RepositoryContext;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.web.servlet.MockMvc;

/** Same @WebMvcTest + explicit SecurityConfig @Import pattern as AnalysisRunControllerTest. */
@WebMvcTest(PullRequestController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class PullRequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PullRequestService pullRequestService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void findAll_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/pull-requests"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("GK-401"));
    }

    @Test
    void findAll_returnsThePagedResultWhenAuthenticated() throws Exception {
        authenticateAs("dev@example.com");
        PullRequestSummaryResponse row = new PullRequestSummaryResponse(
                1L, 7, "Add login page", 10L, "gatekeeper/core", "core", "gatekeeper", "octocat",
                "feature", "main", PullRequestStatus.OPEN, "https://github.com/gatekeeper/core/pull/7",
                9L, AnalysisRunStatus.COMPLETED, VerdictOutcome.BLOCKED, Instant.now(), Instant.now());
        Page<PullRequestSummaryResponse> page = new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1);
        when(pullRequestService.findSummaryPage(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/pull-requests").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content[0].title").value("Add login page"))
                .andExpect(jsonPath("$.data.content[0].repositoryFullName").value("gatekeeper/core"))
                .andExpect(jsonPath("$.data.content[0].repositoryOwner").value("gatekeeper"))
                .andExpect(jsonPath("$.data.content[0].repositoryName").value("core"))
                .andExpect(jsonPath("$.data.content[0].number").value(7))
                .andExpect(jsonPath("$.data.content[0].githubUrl").value("https://github.com/gatekeeper/core/pull/7"))
                .andExpect(jsonPath("$.data.content[0].latestAnalysisRunStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.content[0].latestVerdictOutcome").value("BLOCKED"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void findById_returnsTheDetailResponseWithAnalysisRunHistoryWhenAuthenticated() throws Exception {
        authenticateAs("dev@example.com");
        PullRequestDetailResponse detail = new PullRequestDetailResponse(
                42L, 7, "Add login page", new RepositoryContext(10L, "core", "gatekeeper", "gatekeeper/core"),
                "octocat", "feature", "main", "abc123", PullRequestStatus.OPEN,
                "https://github.com/gatekeeper/core/pull/7", Instant.now(), Instant.now(),
                List.of(new AnalysisRunReference(9L, "abc123", AnalysisRunStatus.COMPLETED,
                        com.gatekeeper.analysisrun.AnalysisRunTriggerReason.OPENED, VerdictOutcome.BLOCKED, Instant.now())));
        when(pullRequestService.findDetailByIdOrThrow(42L)).thenReturn(detail);

        mockMvc.perform(get("/api/v1/pull-requests/42").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.repository.owner").value("gatekeeper"))
                .andExpect(jsonPath("$.data.analysisRuns[0].id").value(9))
                .andExpect(jsonPath("$.data.analysisRuns[0].verdictOutcome").value("BLOCKED"));
    }

    @Test
    void findById_returns404WhenTheServiceThrowsResourceNotFound() throws Exception {
        authenticateAs("dev@example.com");
        when(pullRequestService.findDetailByIdOrThrow(404L))
                .thenThrow(new ResourceNotFoundException("Pull request not found with id: 404"));

        mockMvc.perform(get("/api/v1/pull-requests/404").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }

    @Test
    void findAll_returns400WhenTheStatusFilterIsAnInvalidEnumValue() throws Exception {
        authenticateAs("dev@example.com");

        mockMvc.perform(get("/api/v1/pull-requests?status=NOT_A_STATUS").header("Authorization", "Bearer test-token"))
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
