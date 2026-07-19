package com.gatekeeper.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestRepository;
import com.gatekeeper.pullrequest.PullRequestStatus;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.repository.RepositoryRepository;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.user.UserRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies Milestone 2's Reviewer Decision Workflow (POST/GET
 * .../review-decisions) against real Postgres. Unlike PullRequestQueryIntegrationTest,
 * this seeds a PullRequest/AnalysisRun directly rather than running the full
 * webhook -> pipeline flow: ReviewDecision has no dependency on the Policy/
 * Security/AI engines or GitHub, so no WireMock/GitHub App setup is needed
 * here - only a real AnalysisRun row and a real reviewer User to persist
 * against, proving the entity/repository/controller wiring against a real
 * JPA provider and database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReviewDecisionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private AnalysisRunRepository analysisRunRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Value("${gatekeeper.bootstrap.admin.email}")
    private String bootstrapAdminEmail;

    private String bearerToken;
    private Long analysisRunId;

    @BeforeAll
    void seedAnalysisRun() {
        Organization organization = organizationService.getDefaultOrganization();
        Repository repository = repositoryRepository.save(Repository.builder()
                .organization(organization)
                .name("core")
                .fullName("review-decision-it/core")
                .owner("review-decision-it")
                .active(true)
                .build());
        PullRequest pullRequest = pullRequestRepository.save(PullRequest.builder()
                .repository(repository)
                .githubPrId(900_301L)
                .number(51)
                .title("Add feature")
                .authorLogin("octocat")
                .sourceBranch("feature")
                .targetBranch("main")
                .headSha("sha-review-decision-it")
                .status(PullRequestStatus.OPEN)
                .build());
        AnalysisRun analysisRun = analysisRunRepository.save(AnalysisRun.builder()
                .pullRequest(pullRequest)
                .commitSha("sha-review-decision-it")
                .triggerReason(AnalysisRunTriggerReason.OPENED)
                .status(AnalysisRunStatus.COMPLETED)
                .build());
        analysisRunId = analysisRun.getId();
    }

    @BeforeEach
    void authenticate() {
        var admin = userRepository.findByEmailIgnoreCase(bootstrapAdminEmail).orElseThrow();
        bearerToken = "Bearer " + jwtService.generateAccessToken(
                admin.getId(), admin.getEmail(), admin.getRole().getName(), admin.getOrganization().getId());
    }

    @Test
    void reviewDecisionEndpoints_recordAndListDecisionsAgainstARealAnalysisRun() throws Exception {
        mockMvc.perform(post("/api/v1/analysis-runs/" + analysisRunId + "/review-decisions")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"comment\":\"Needs another pass\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.decision").value("REJECTED"))
                .andExpect(jsonPath("$.data.comment").value("Needs another pass"))
                .andExpect(jsonPath("$.data.reviewerName").value("GateKeeper Administrator"));

        mockMvc.perform(post("/api/v1/analysis-runs/" + analysisRunId + "/review-decisions")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));

        // History is newest first: the second (APPROVED) decision precedes the first (REJECTED).
        mockMvc.perform(get("/api/v1/analysis-runs/" + analysisRunId + "/review-decisions")
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].decision").value("APPROVED"))
                .andExpect(jsonPath("$.data[1].decision").value("REJECTED"));
    }

    @Test
    void create_returns404ForAnUnknownAnalysisRunId() throws Exception {
        mockMvc.perform(post("/api/v1/analysis-runs/999999999/review-decisions")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }

    @Test
    void create_returns400ForAnInvalidDecisionValue() throws Exception {
        mockMvc.perform(post("/api/v1/analysis-runs/" + analysisRunId + "/review-decisions")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GK-400"));
    }

    @Test
    void findHistory_returns404ForAnUnknownAnalysisRunId() throws Exception {
        mockMvc.perform(get("/api/v1/analysis-runs/999999999/review-decisions").header("Authorization", bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }
}
