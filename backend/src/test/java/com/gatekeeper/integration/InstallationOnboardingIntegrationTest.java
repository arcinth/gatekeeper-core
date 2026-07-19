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
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestRepository;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.repository.RepositoryRepository;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.awaitility.Awaitility;
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

        // pull_request analysis: PullRequest persisted and AnalysisRun reaches
        // COMPLETED - AnalysisExecutionService's own AFTER_COMMIT/@Async hop
        // (Policy + Security engines, then AnalysisResultPersistenceService's commit).
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();
            var run = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-onboarding")
                    .orElseThrow();
            assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
        });

        // GitHub Check Run publication is a second, separate AFTER_COMMIT hop
        // chained after the first via VerdictProducedEvent - GitHubCheckRunPublisher
        // -> GitHubCheckRunService, which makes its own two real HTTP round trips
        // (installation token, then check-run creation) before its own commit.
        // Awaited on its own, with its own full budget matching every single-hop
        // wait elsewhere in this suite, rather than folded into the assertion
        // above: it is a genuinely distinct step with its own latency, not a
        // continuation of the same one.
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();
            var run = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-onboarding")
                    .orElseThrow();
            assertThat(run.getGithubCheckRunId()).isEqualTo(9988L);
        });
        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo("/repos/" + REPOSITORY_FULL_NAME + "/check-runs")));
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
