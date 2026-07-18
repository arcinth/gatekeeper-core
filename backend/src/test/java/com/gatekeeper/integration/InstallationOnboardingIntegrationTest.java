package com.gatekeeper.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.github.GitHubInstallationRepository;
import com.gatekeeper.github.dto.InstallationWebhookPayload;
import com.gatekeeper.github.dto.PullRequestWebhookPayload;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.BranchRef;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.InstallationData;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.PullRequestData;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.RepositoryData;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.UserData;
import com.gatekeeper.orchestration.GitHubCheckRunService;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestRepository;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.repository.RepositoryRepository;
import com.gatekeeper.verdict.VerdictRepository;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.awaitility.Awaitility;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Reproduces and proves the fix for the release-blocking bug: after
 * "installation.created" is received, GateKeeper never learned which
 * repositories the installation could see, so a subsequent "pull_request"
 * webhook for a real repository (never manually seeded here, deliberately -
 * that's the whole point) always failed RepositoryLookupService and was
 * skipped. Exercises the complete chain the fix requires end to end, against
 * real Postgres, with no repository ever created except through the webhook
 * + sync flow itself:
 * <p>
 * installation.created -&gt; GitHubInstallationService persists the
 * installation -&gt; InstallationRepositorySyncRequestedEvent -&gt;
 * GitHubRepositorySyncService calls GET /installation/repositories -&gt;
 * RepositoryService.synchronizeFromInstallation upserts the Repository row
 * -&gt; a later pull_request webhook for that same github_repository_id
 * resolves through RepositoryLookupService -&gt; the deterministic pipeline
 * runs -&gt; VerdictProducedEvent -&gt; GitHubCheckRunService publishes a
 * Check Run.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InstallationOnboardingIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final WireMockServer wireMockServer =
            new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    private static final String testAppPrivateKeyPem = generateTestAppPrivateKeyPem();

    private static String generateTestAppPrivateKeyPem() {
        try {
            KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
            String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded());
            return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
        WireMock.configureFor(wireMockServer.port());
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("gatekeeper.github.api.base-url", () -> wireMockServer.baseUrl());
        registry.add("gatekeeper.github.app.id", () -> "12345");
        registry.add("gatekeeper.github.app.private-key", () -> testAppPrivateKeyPem);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GitHubInstallationRepository gitHubInstallationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private AnalysisRunRepository analysisRunRepository;

    @Autowired
    private VerdictRepository verdictRepository;

    @Autowired
    private GitHubCheckRunService gitHubCheckRunService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Value("${gatekeeper.github.webhook.secret}")
    private String webhookSecret;

    private static final long INSTALLATION_ID = 147_259_549L;
    private static final long GITHUB_REPOSITORY_ID = 1_299_531_781L;
    private static final String REPOSITORY_FULL_NAME = "arcinth/gatekeeper-core";

    @BeforeEach
    void resetStubs() {
        wireMockServer.resetAll();
        stubFor(WireMock.post(urlPathEqualTo("/app/installations/" + INSTALLATION_ID + "/access_tokens"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"ghs_wiremock_token\",\"expires_at\":\"2099-01-01T00:00:00Z\"}")));
    }

    @Test
    void installationCreated_synchronizesRepositoriesSoALaterPullRequestWebhookIsNotSkipped() throws Exception {
        stubFor(WireMock.get(urlPathEqualTo("/installation/repositories"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"total_count\":1,\"repositories\":[{\"id\":" + GITHUB_REPOSITORY_ID
                                + ",\"name\":\"gatekeeper-core\",\"full_name\":\"" + REPOSITORY_FULL_NAME + "\"}]}")));

        performInstallationWebhook("created", "delivery-install-1").andExpect(status().isOk());

        // installation.created -> GitHubInstallationService persists the installation.
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).isPresent());

        // InstallationRepositorySyncRequestedEvent -> GitHubRepositorySyncService ->
        // RepositoryService.synchronizeFromInstallation: the repository now exists
        // without ever having been manually seeded by this test.
        //
        // Read inside an explicit, short-lived transaction (rather than relying on
        // repositoryRepository's own per-call transaction) so the Hibernate session
        // is still open when the lazy githubInstallation association below is
        // navigated - GitHubRepositorySyncService's listener is deliberately
        // @TransactionalEventListener(phase = AFTER_COMMIT), so wrapping this whole
        // test in @Transactional would prevent that commit - and therefore the sync
        // itself - from ever happening.
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> transactionTemplate.executeWithoutResult(status -> {
            Repository repository = repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID).orElseThrow();
            assertThat(repository.getFullName()).isEqualTo(REPOSITORY_FULL_NAME);
            assertThat(repository.getOwner()).isEqualTo("arcinth");
            assertThat(repository.isActive()).isTrue();
            assertThat(repository.getGithubInstallation().getInstallationId()).isEqualTo(INSTALLATION_ID);
        }));
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/installation/repositories")));

        // Now a pull_request webhook for that same GitHub repository id must resolve
        // through RepositoryLookupService instead of being skipped as unlinked.
        stubFor(WireMock.get(urlPathEqualTo("/repos/" + REPOSITORY_FULL_NAME + "/pulls/7/files"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("[{\"filename\":\"src/Example.java\",\"status\":\"modified\",\"changes\":1,"
                                + "\"patch\":\"@@ -1 +1,2 @@\\n class Example {\\n+    // TODO: wire this up\\n }\"}]")));
        stubFor(WireMock.post(urlPathEqualTo("/repos/" + REPOSITORY_FULL_NAME + "/check-runs"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":9988}")));

        long githubPrId = 900_301L;
        performPullRequestWebhook(githubPrId, 7, "sha-onboarding", "delivery-pr-1").andExpect(status().isOk());

        // pull_request analysis: PullRequest persisted, AnalysisRun reaches COMPLETED,
        // and (VerdictProducedEvent having fired) a GitHub Check Run is published.
        // TEMPORARY DIAGNOSTIC - remove once the check-run publication gap is resolved.
        // Deliberately embedded in the *thrown* AssertionError's message, not just
        // logged: application log.info/log.error output was added in an earlier
        // iteration but isn't visible in the CI job's summary view, so evidence has
        // to travel inside the failure JUnit/Surefire itself reports - the same
        // channel that already surfaced the "ConditionTimeout" text being debugged.
        TransactionTemplate diagnosticTransaction = new TransactionTemplate(transactionManager);
        try {
            Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();
                var run = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-onboarding")
                        .orElseThrow();
                assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
                assertThat(run.getGithubCheckRunId()).isEqualTo(9988L);
            });
        } catch (ConditionTimeoutException ex) {
            throw new AssertionError(diagnosticReport(githubPrId, diagnosticTransaction), ex);
        }
        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/repos/" + REPOSITORY_FULL_NAME + "/check-runs")));
    }

    /**
     * Best-effort: DB reads run inside their own real transaction (needed for the
     * lazy githubInstallation association); if gathering diagnostics itself throws,
     * that's recorded in the map rather than replacing the original
     * ConditionTimeoutException that triggered this in the first place.
     */
    private String diagnosticReport(long githubPrId, TransactionTemplate transactionTemplate) {
        Map<String, Object> facts = new LinkedHashMap<>();
        transactionTemplate.executeWithoutResult(status -> {
            Optional<PullRequest> pullRequest = pullRequestRepository.findByGithubPrId(githubPrId);
            facts.put("pullRequestExists", pullRequest.isPresent());

            Optional<AnalysisRun> run = pullRequest.flatMap(pr ->
                    analysisRunRepository.findByPullRequestIdAndCommitSha(pr.getId(), "sha-onboarding"));
            facts.put("analysisRunExists", run.isPresent());
            facts.put("analysisRunId", run.map(AnalysisRun::getId).orElse(null));
            facts.put("analysisRunStatus", run.map(AnalysisRun::getStatus).map(Object::toString).orElse(null));
            facts.put("analysisRunCheckRunId", run.map(AnalysisRun::getGithubCheckRunId).orElse(null));
            facts.put("analysisRunFailureReason", run.map(AnalysisRun::getFailureReason).orElse(null));

            facts.put("verdictExists", run.map(r -> verdictRepository.findByAnalysisRunId(r.getId()).isPresent())
                    .orElse(false));

            Optional<Repository> repository = repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID);
            facts.put("repositoryExists", repository.isPresent());
            facts.put("repositoryFullName", repository.map(Repository::getFullName).orElse(null));
            facts.put("repositoryInstallationExists",
                    repository.map(r -> r.getGithubInstallation() != null).orElse(false));
            facts.put("installationId", repository
                    .map(Repository::getGithubInstallation)
                    .map(installation -> (Object) installation.getInstallationId())
                    .orElse(null));
        });

        int repoListCalls = wireMockServer.findAll(
                getRequestedFor(urlPathEqualTo("/installation/repositories"))).size();
        int accessTokenCalls = wireMockServer.findAll(postRequestedFor(
                urlPathEqualTo("/app/installations/" + INSTALLATION_ID + "/access_tokens"))).size();
        int filesCalls = wireMockServer.findAll(
                getRequestedFor(urlPathEqualTo("/repos/" + REPOSITORY_FULL_NAME + "/pulls/7/files"))).size();
        int checkRunCalls = wireMockServer.findAll(postRequestedFor(
                urlPathEqualTo("/repos/" + REPOSITORY_FULL_NAME + "/check-runs"))).size();
        List<String> allRequests = wireMockServer.getAllServeEvents().stream()
                .map(event -> event.getRequest().getMethod() + " " + event.getRequest().getUrl())
                .toList();

        StringBuilder report = new StringBuilder();
        report.append("=== DATABASE ===\n\n");
        report.append("PullRequest exists: ").append(facts.get("pullRequestExists")).append("\n\n");
        report.append("AnalysisRun exists: ").append(facts.get("analysisRunExists")).append("\n\n");
        report.append("AnalysisRun.status: ").append(facts.get("analysisRunStatus")).append('\n');
        report.append("AnalysisRun.githubCheckRunId: ").append(facts.get("analysisRunCheckRunId")).append('\n');
        report.append("AnalysisRun.failureReason: ").append(facts.get("analysisRunFailureReason")).append("\n\n");
        report.append("Verdict exists: ").append(facts.get("verdictExists")).append("\n\n");
        report.append("Repository exists: ").append(facts.get("repositoryExists")).append('\n');
        report.append("Repository.fullName: ").append(facts.get("repositoryFullName")).append('\n');
        report.append("Repository.githubInstallation exists: ")
                .append(facts.get("repositoryInstallationExists")).append('\n');
        report.append("InstallationId: ").append(facts.get("installationId")).append("\n\n");

        report.append("=== WIREMOCK ===\n\n");
        report.append("GET /installation/repositories : ").append(repoListCalls).append('\n');
        report.append("POST /access_tokens : ").append(accessTokenCalls).append('\n');
        report.append("GET /pulls/{n}/files : ").append(filesCalls).append('\n');
        report.append("POST /check-runs : ").append(checkRunCalls).append("\n\n");
        report.append("All requests:\n");
        allRequests.forEach(request -> report.append(request).append('\n'));

        report.append("\n=== DIRECT RE-INVOCATION ===\n\n");
        report.append(reinvokePublishForVerdict(facts)).append('\n');

        report.append("\n=== CONCLUSION ===\n");
        report.append("Last confirmed successful checkpoint: ").append(lastSuccessfulCheckpoint(facts, checkRunCalls));
        return report.toString();
    }

    /**
     * Calls the exact same GitHubCheckRunService.publishForVerdict(...) bean
     * method GitHubCheckRunPublisher calls - but directly, bypassing that
     * listener's own try/catch entirely. If a real exception is being thrown and
     * silently swallowed there (its log.error output isn't visible in this CI
     * job's summary), it surfaces here instead, uncaught, in the test's own
     * failure output. Only attempted if githubCheckRunId is still null - if it's
     * already set, there's nothing left to reproduce.
     */
    private String reinvokePublishForVerdict(Map<String, Object> facts) {
        if (!Boolean.TRUE.equals(facts.get("analysisRunExists")) || facts.get("analysisRunCheckRunId") != null) {
            return "Skipped (no AnalysisRun to retry, or githubCheckRunId is already set).";
        }
        Long analysisRunId = (Long) facts.get("analysisRunId");
        try {
            gitHubCheckRunService.publishForVerdict(analysisRunId);
            return "publishForVerdict(" + analysisRunId + ") returned normally on direct re-invocation "
                    + "(no exception) - re-check githubCheckRunId after this report.";
        } catch (RuntimeException ex) {
            java.io.StringWriter stackTrace = new java.io.StringWriter();
            ex.printStackTrace(new java.io.PrintWriter(stackTrace));
            return "publishForVerdict(" + analysisRunId + ") THREW on direct re-invocation:\n" + stackTrace;
        }
    }

    /**
     * Walks the execution path in order and reports the last checkpoint that
     * actually succeeded - the checkpoint immediately after it is, by construction,
     * the first point where expected behavior diverged.
     */
    private String lastSuccessfulCheckpoint(Map<String, Object> facts, int checkRunCalls) {
        record Checkpoint(String name, boolean success) {
        }
        List<Checkpoint> checkpoints = List.of(
                new Checkpoint("PullRequest persisted", Boolean.TRUE.equals(facts.get("pullRequestExists"))),
                new Checkpoint("AnalysisRun created", Boolean.TRUE.equals(facts.get("analysisRunExists"))),
                new Checkpoint("AnalysisRun reached COMPLETED",
                        "COMPLETED".equals(facts.get("analysisRunStatus"))),
                new Checkpoint("Verdict persisted (VerdictProducedEvent published immediately after)",
                        Boolean.TRUE.equals(facts.get("verdictExists"))),
                new Checkpoint("GitHub check-run POST was received by WireMock", checkRunCalls > 0),
                new Checkpoint("AnalysisRun.githubCheckRunId persisted",
                        facts.get("analysisRunCheckRunId") != null));

        String last = "none - PullRequest was never persisted";
        for (Checkpoint checkpoint : checkpoints) {
            if (!checkpoint.success()) {
                break;
            }
            last = checkpoint.name();
        }
        return last;
    }

    private org.springframework.test.web.servlet.ResultActions performInstallationWebhook(String action, String deliveryId)
            throws Exception {
        InstallationWebhookPayload payload = new InstallationWebhookPayload(
                action,
                new InstallationWebhookPayload.InstallationData(
                        INSTALLATION_ID,
                        new InstallationWebhookPayload.AccountData(42L, "arcinth", "User"),
                        "all",
                        Map.of("contents", "read", "pull_requests", "write")));
        byte[] body = objectMapper.writeValueAsBytes(payload);
        return mockMvc.perform(post("/api/v1/github/webhook")
                .header("X-Hub-Signature-256", sign(body))
                .header("X-GitHub-Event", "installation")
                .header("X-GitHub-Delivery", deliveryId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions performPullRequestWebhook(
            long githubPrId, int number, String headSha, String deliveryId) throws Exception {
        PullRequestWebhookPayload payload = new PullRequestWebhookPayload(
                "opened",
                new PullRequestData(githubPrId, number, "Add example", new UserData("arcinth"),
                        new BranchRef("feature", headSha), new BranchRef("main", "base-sha"), "open", false),
                new RepositoryData(GITHUB_REPOSITORY_ID, REPOSITORY_FULL_NAME, "main"),
                new InstallationData(INSTALLATION_ID));
        byte[] body = objectMapper.writeValueAsBytes(payload);
        return mockMvc.perform(post("/api/v1/github/webhook")
                .header("X-Hub-Signature-256", sign(body))
                .header("X-GitHub-Event", "pull_request")
                .header("X-GitHub-Delivery", deliveryId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(body));
    }

    private String sign(byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
