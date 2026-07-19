package com.gatekeeper.reviewdecision;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.config.SecurityConfig;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.reviewdecision.dto.ReviewDecisionResponse;
import com.gatekeeper.role.Role;
import com.gatekeeper.security.CustomUserDetailsService;
import com.gatekeeper.security.JwtAccessDeniedHandler;
import com.gatekeeper.security.JwtAuthenticationEntryPoint;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.security.SecurityUser;
import com.gatekeeper.user.User;
import io.jsonwebtoken.Claims;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewDecisionController.class)
@Import({SecurityConfig.class, JwtAuthenticationEntryPoint.class, JwtAccessDeniedHandler.class})
class ReviewDecisionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReviewDecisionService reviewDecisionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void create_returns401WithoutAJwt() throws Exception {
        mockMvc.perform(post("/api/v1/analysis-runs/1/review-decisions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void create_returns403WhenTheCallerIsADeveloper() throws Exception {
        // DEVELOPER has WORKSPACE_READ but not REVIEW_DECISION_CREATE (Milestone 5:
        // RBAC Enforcement) - the exact gap the milestone exists to close.
        authenticateAs("developer@example.com", 9L, "DEVELOPER");

        mockMvc.perform(post("/api/v1/analysis-runs/1/review-decisions")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_returns201AndTheRecordedDecisionWhenAuthenticated() throws Exception {
        authenticateAs("reviewer@example.com", 9L, "TECHNICAL_LEAD");
        ReviewDecisionResponse response =
                new ReviewDecisionResponse(42L, 1L, ReviewDecisionType.APPROVED, "Looks good", 9L, "Reviewer Name", Instant.now());
        when(reviewDecisionService.create(eq(1L), eq(9L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/analysis-runs/1/review-decisions")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"comment\":\"Looks good\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value(42))
                .andExpect(jsonPath("$.data.decision").value("APPROVED"))
                .andExpect(jsonPath("$.data.reviewerName").value("Reviewer Name"));
    }

    @Test
    void create_returns400ForAnInvalidDecisionValue() throws Exception {
        authenticateAs("reviewer@example.com", 9L, "TECHNICAL_LEAD");

        mockMvc.perform(post("/api/v1/analysis-runs/1/review-decisions")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GK-400"));
    }

    @Test
    void create_returns422WhenDecisionIsMissing() throws Exception {
        authenticateAs("reviewer@example.com", 9L, "TECHNICAL_LEAD");

        mockMvc.perform(post("/api/v1/analysis-runs/1/review-decisions")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("GK-422"));
    }

    @Test
    void create_returns404WhenTheAnalysisRunDoesNotExist() throws Exception {
        authenticateAs("reviewer@example.com", 9L, "TECHNICAL_LEAD");
        when(reviewDecisionService.create(eq(404L), eq(9L), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new ResourceNotFoundException("AnalysisRun not found with id: 404"));

        mockMvc.perform(post("/api/v1/analysis-runs/404/review-decisions")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }

    @Test
    void findHistory_returnsTheDecisionHistoryWhenAuthenticated() throws Exception {
        // DEVELOPER deliberately used here: WORKSPACE_READ (unlike REVIEW_DECISION_CREATE)
        // is granted to every role, so reading history must keep working for a role
        // that cannot submit a decision.
        authenticateAs("reviewer@example.com", 9L, "DEVELOPER");
        when(reviewDecisionService.findHistory(1L)).thenReturn(List.of(
                new ReviewDecisionResponse(2L, 1L, ReviewDecisionType.APPROVED, null, 9L, "Reviewer Name", Instant.now()),
                new ReviewDecisionResponse(1L, 1L, ReviewDecisionType.REJECTED, "Needs work", 9L, "Reviewer Name", Instant.now())));

        mockMvc.perform(get("/api/v1/analysis-runs/1/review-decisions").header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[1].id").value(1));
    }

    @Test
    void findHistory_returns404WhenTheAnalysisRunDoesNotExist() throws Exception {
        authenticateAs("reviewer@example.com", 9L, "DEVELOPER");
        when(reviewDecisionService.findHistory(404L))
                .thenThrow(new ResourceNotFoundException("AnalysisRun not found with id: 404"));

        mockMvc.perform(get("/api/v1/analysis-runs/404/review-decisions").header("Authorization", "Bearer test-token"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }

    /**
     * Unlike ReportControllerTest's authenticateAs (which mocks a plain Spring
     * Security User), this controller resolves @AuthenticationPrincipal as the
     * app's own SecurityUser, so the mocked UserDetails returned here must
     * actually be a SecurityUser - a plain User would throw a ClassCastException
     * when the argument resolver casts it.
     */
    private void authenticateAs(String email, Long userId, String roleName) {
        Claims claims = mock(Claims.class);
        when(claims.get(JwtService.CLAIM_TYPE, String.class)).thenReturn(JwtService.TOKEN_TYPE_ACCESS);
        when(claims.get(JwtService.CLAIM_EMAIL, String.class)).thenReturn(email);
        when(jwtService.parseClaims(anyString())).thenReturn(claims);

        User user = User.builder()
                .email(email)
                .passwordHash("x")
                .fullName("Reviewer Name")
                .organization(Organization.builder().name("Acme").build())
                .role(Role.builder().name(roleName).build())
                .enabled(true)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        SecurityUser securityUser = new SecurityUser(user);
        when(customUserDetailsService.loadUserByUsername(eq(email))).thenReturn(securityUser);
    }
}
