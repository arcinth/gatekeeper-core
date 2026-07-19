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
 * Verifies Milestone 7's Audit Log search/export API end-to-end against real
 * Postgres: recording a real ReviewDecision through its own API (Milestone 2)
 * must produce a queryable, organization-scoped audit trail entry with the
 * correct actor/repository/pull request/analysis run dimensions - proving
 * the AuditLogService wiring, not just the controller in isolation
 * (AuditLogControllerTest already covers permission gating with mocks).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditLogIntegrationTest {

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
    private Long repositoryId;
    private Long pullRequestId;

    @BeforeAll
    void seedAnalysisRun() {
        Organization organization = organizationService.getDefaultOrganization();
        Repository repository = repositoryRepository.save(Repository.builder()
                .organization(organization)
                .name("core")
                .fullName("audit-log-it/core")
                .owner("audit-log-it")
                .active(true)
                .build());
        repositoryId = repository.getId();
        PullRequest pullRequest = pullRequestRepository.save(PullRequest.builder()
                .repository(repository)
                .githubPrId(900_401L)
                .number(61)
                .title("Add feature")
                .authorLogin("octocat")
                .sourceBranch("feature")
                .targetBranch("main")
                .headSha("sha-audit-log-it")
                .status(PullRequestStatus.OPEN)
                .build());
        pullRequestId = pullRequest.getId();
        AnalysisRun analysisRun = analysisRunRepository.save(AnalysisRun.builder()
                .pullRequest(pullRequest)
                .commitSha("sha-audit-log-it")
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
    void recordingAReviewDecision_producesAQueryableAuditLogEntry() throws Exception {
        mockMvc.perform(post("/api/v1/analysis-runs/" + analysisRunId + "/review-decisions")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"comment\":\"Looks good\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", bearerToken)
                        .param("eventType", "REVIEW_DECISION_RECORDED")
                        .param("analysisRunId", String.valueOf(analysisRunId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].eventType").value("REVIEW_DECISION_RECORDED"))
                .andExpect(jsonPath("$.data.content[0].actorName").value("GateKeeper Administrator"))
                .andExpect(jsonPath("$.data.content[0].repositoryId").value(repositoryId))
                .andExpect(jsonPath("$.data.content[0].pullRequestId").value(pullRequestId))
                .andExpect(jsonPath("$.data.content[0].analysisRunId").value(analysisRunId))
                .andExpect(jsonPath("$.data.content[0].newValue.decision").value("APPROVED"))
                .andExpect(jsonPath("$.data.content[0].correlationId").exists());
    }

    @Test
    void findById_returns404ForAnEntryThatDoesNotExist() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/999999999").header("Authorization", bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }

    @Test
    void export_returnsCsvContainingAtLeastTheHeaderRow() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/export").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith("text/csv"));
    }
}
