package com.gatekeeper.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.github.GitHubInstallation;
import com.gatekeeper.github.GitHubInstallationRepository;
import com.gatekeeper.github.dto.PullRequestWebhookPayload;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.BranchRef;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.InstallationData;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.PullRequestData;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.RepositoryData;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.UserData;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestRepository;
import com.gatekeeper.pullrequest.PullRequestStatus;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.repository.RepositoryRepository;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the full ingestion pipeline (Sprint 2 Architecture, Section 10)
 * against a real PostgreSQL instance: HTTP webhook in, signature verification,
 * repository lookup, and the actual database rows the unit tests only assert
 * indirectly through mocks. Testcontainers rather than H2: this project commits
 * to PostgreSQL-specific behavior (partial/plain unique constraints, JSONB
 * groundwork for later sprints), so an in-memory substitute would validate the
 * wrong database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PullRequestWebhookIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        // @TestInstance(PER_CLASS) makes SpringExtension construct the shared
        // test instance - and thus the ApplicationContext - before
        // TestcontainersExtension.beforeAll() gets a chance to start POSTGRES.
        // start() is idempotent, so calling it here guarantees the container
        // is running before this property is ever resolved, regardless of
        // that ordering.
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrganizationService organizationService;

    @Autowired
    private GitHubInstallationRepository gitHubInstallationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private AnalysisRunRepository analysisRunRepository;

    @Value("${gatekeeper.github.webhook.secret}")
    private String webhookSecret;

    private static final long LINKED_INSTALLATION_ID = 555L;
    private static final long LINKED_GITHUB_REPOSITORY_ID = 99L;

    private Repository linkedRepository;

    @BeforeAll
    void seedLinkedRepository() {
        Organization organization = organizationService.getDefaultOrganization();

        GitHubInstallation installation = gitHubInstallationRepository.save(GitHubInstallation.builder()
                .organization(organization)
                .installationId(LINKED_INSTALLATION_ID)
                .githubAccountLogin("octocat")
                .build());

        linkedRepository = repositoryRepository.save(Repository.builder()
                .organization(organization)
                .name("core")
                .fullName("gatekeeper/core")
                .active(true)
                .githubRepositoryId(LINKED_GITHUB_REPOSITORY_ID)
                .githubInstallation(installation)
                .build());
    }

    @Test
    void openedWebhook_persistsPullRequestAndQueuesTheAnalysisRunForExecution() throws Exception {
        long githubPrId = 100_001L;

        performWebhook(payload("opened", githubPrId, 7, "sha-opened", "open", false), "delivery-1")
                .andExpect(status().isOk());

        PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();
        assertThat(pullRequest.getRepository().getId()).isEqualTo(linkedRepository.getId());
        assertThat(pullRequest.getStatus()).isEqualTo(PullRequestStatus.OPEN);
        assertThat(pullRequest.getHeadSha()).isEqualTo("sha-opened");

        var run = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-opened").orElseThrow();
        assertThat(run.getTriggerReason()).isEqualTo(AnalysisRunTriggerReason.OPENED);
        // The webhook response returns as soon as the run is QUEUED, before
        // AnalysisExecutionService's async listener has necessarily run -
        // RECEIVED is no longer a valid assertion here (Milestone 4: the
        // orchestrator transitions RECEIVED -> QUEUED synchronously, in the
        // same transaction, before responding to GitHub).
        assertThat(run.getStatus()).isIn(AnalysisRunStatus.QUEUED, AnalysisRunStatus.IN_PROGRESS, AnalysisRunStatus.FAILED);
    }

    /**
     * No real GitHub App is configured for this test class (no WireMock stub,
     * no valid private key), so AnalysisExecutionService's async execution is
     * expected to fail once it tries to mint an installation token - this
     * test exists to prove that failure path completes and is recorded
     * correctly, not to prove the happy path (see
     * PolicyEngineExecutionIntegrationTest for that, with GitHub stubbed via
     * WireMock).
     */
    @Test
    void openedWebhook_eventuallyMarksTheAnalysisRunFailedWhenGitHubCredentialsAreNotConfigured() throws Exception {
        long githubPrId = 100_007L;

        performWebhook(payload("opened", githubPrId, 13, "sha-no-creds", "open", false), "delivery-8")
                .andExpect(status().isOk());

        PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var run = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-no-creds")
                    .orElseThrow();
            assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
            assertThat(run.getFailureReason()).contains("GitHub App private key");
        });
    }

    @Test
    void redeliveredWebhook_doesNotCreateDuplicateRows() throws Exception {
        long githubPrId = 100_002L;
        byte[] body = payload("opened", githubPrId, 8, "sha-dup", "open", false);

        performWebhook(body, "delivery-2").andExpect(status().isOk());
        performWebhook(body, "delivery-2-retry").andExpect(status().isOk());

        assertThat(pullRequestRepository.findByGithubPrId(githubPrId)).isPresent();
        PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();
        long analysisRunCount = analysisRunRepository.findAll().stream()
                .filter(run -> run.getPullRequest().getId().equals(pullRequest.getId()))
                .count();
        assertThat(analysisRunCount).isEqualTo(1);
    }

    @Test
    void synchronizeWebhook_updatesHeadShaAndCreatesASecondAnalysisRunWithoutTouchingTheFirst() throws Exception {
        long githubPrId = 100_003L;

        performWebhook(payload("opened", githubPrId, 9, "sha-1", "open", false), "delivery-3a")
                .andExpect(status().isOk());
        performWebhook(payload("synchronize", githubPrId, 9, "sha-2", "open", false), "delivery-3b")
                .andExpect(status().isOk());

        PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();
        assertThat(pullRequest.getHeadSha()).isEqualTo("sha-2");

        var firstRun = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-1");
        var secondRun = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-2");
        assertThat(firstRun).isPresent();
        assertThat(secondRun).isPresent();
        assertThat(firstRun.get().getTriggerReason()).isEqualTo(AnalysisRunTriggerReason.OPENED);
        assertThat(secondRun.get().getTriggerReason()).isEqualTo(AnalysisRunTriggerReason.SYNCHRONIZE);
    }

    @Test
    void closedWebhook_updatesStatusToMergedWithoutCreatingAnAnalysisRun() throws Exception {
        long githubPrId = 100_004L;

        performWebhook(payload("opened", githubPrId, 10, "sha-x", "open", false), "delivery-4a")
                .andExpect(status().isOk());
        performWebhook(payload("closed", githubPrId, 10, "sha-x", "closed", true), "delivery-4b")
                .andExpect(status().isOk());

        PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();
        assertThat(pullRequest.getStatus()).isEqualTo(PullRequestStatus.MERGED);

        long analysisRunCount = analysisRunRepository.findAll().stream()
                .filter(run -> run.getPullRequest().getId().equals(pullRequest.getId()))
                .count();
        assertThat(analysisRunCount).isEqualTo(1);
    }

    @Test
    void webhookForAnUnknownRepository_isAcknowledgedButPersistsNothing() throws Exception {
        long githubPrId = 100_005L;
        long unknownGithubRepositoryId = 424242L;
        PullRequestWebhookPayload payload = new PullRequestWebhookPayload(
                "opened",
                new PullRequestData(githubPrId, 11, "Orphan PR", new UserData("octocat"),
                        new BranchRef("feature", "sha-orphan"), new BranchRef("main", "base"), "open", false),
                new RepositoryData(unknownGithubRepositoryId, "someone-else/repo", "main"),
                new InstallationData(LINKED_INSTALLATION_ID));

        performWebhook(objectMapper.writeValueAsBytes(payload), "delivery-5").andExpect(status().isOk());

        assertThat(pullRequestRepository.findByGithubPrId(githubPrId)).isEmpty();
    }

    @Test
    void invalidSignature_isRejectedWithoutPersistingAnything() throws Exception {
        long githubPrId = 100_006L;
        byte[] body = payload("opened", githubPrId, 12, "sha-invalid", "open", false);

        mockMvc.perform(post("/api/v1/github/webhook")
                        .header("X-Hub-Signature-256", "sha256=0000000000000000000000000000000000000000000000000000000000000000")
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "delivery-6")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertThat(pullRequestRepository.findByGithubPrId(githubPrId)).isEmpty();
    }

    @Test
    void unrecognizedEventType_isAcknowledgedWithoutInvokingPullRequestProcessing() throws Exception {
        byte[] body = "{\"zen\":\"Speak like a human.\"}".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(post("/api/v1/github/webhook")
                        .header("X-Hub-Signature-256", sign(body))
                        .header("X-GitHub-Event", "ping")
                        .header("X-GitHub-Delivery", "delivery-7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private ResultActions performWebhook(byte[] body, String deliveryId) throws Exception {
        return mockMvc.perform(post("/api/v1/github/webhook")
                .header("X-Hub-Signature-256", sign(body))
                .header("X-GitHub-Event", "pull_request")
                .header("X-GitHub-Delivery", deliveryId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private byte[] payload(String action, long githubPrId, int number, String headSha, String state, boolean merged)
            throws Exception {
        PullRequestWebhookPayload payload = new PullRequestWebhookPayload(
                action,
                new PullRequestData(githubPrId, number, "Add login page", new UserData("octocat"),
                        new BranchRef("feature", headSha), new BranchRef("main", "base-sha"), state, merged),
                new RepositoryData(LINKED_GITHUB_REPOSITORY_ID, "gatekeeper/core", "main"),
                new InstallationData(LINKED_INSTALLATION_ID));
        return objectMapper.writeValueAsBytes(payload);
    }

    private String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
