package com.gatekeeper.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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
import com.gatekeeper.policyfinding.PolicyFindingRepository;
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
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * The happy-path counterpart to PullRequestWebhookIntegrationTest's
 * credentials-not-configured failure test: GitHub's App-token and
 * changed-files endpoints are stubbed with WireMock so the full pipeline -
 * webhook in, async execution, real GitHub App JWT signing, Policy Engine
 * evaluation, finding persistence - runs end to end against a real Postgres
 * instance with a genuinely successful outcome.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PolicyEngineExecutionIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    // @TestInstance(PER_CLASS) means @BeforeAll can no longer be trusted to
    // run before @DynamicPropertySource (see the POSTGRES.start() comment
    // below) - wireMockServer and testAppPrivateKeyPem are constructed here,
    // at class-initialization time, instead of in a @BeforeAll method, so
    // both are guaranteed to exist by the time @DynamicPropertySource reads
    // them.
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
        // @TestInstance(PER_CLASS) makes SpringExtension construct the shared
        // test instance - and thus the ApplicationContext - before
        // TestcontainersExtension.beforeAll() gets a chance to start POSTGRES.
        // start() is idempotent, so calling it here guarantees the container
        // is running before this property is ever resolved, regardless of
        // that ordering.
        POSTGRES.start();
        if (!wireMockServer.isRunning()) {
            wireMockServer.start();
        }
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
    private OrganizationService organizationService;

    @Autowired
    private GitHubInstallationRepository gitHubInstallationRepository;

    @Autowired
    private RepositoryRepository repositoryRepository;

    @Autowired
    private PullRequestRepository pullRequestRepository;

    @Autowired
    private AnalysisRunRepository analysisRunRepository;

    @Autowired
    private PolicyFindingRepository policyFindingRepository;

    @Value("${gatekeeper.github.webhook.secret}")
    private String webhookSecret;

    private static final long LINKED_INSTALLATION_ID = 777L;
    private static final long LINKED_GITHUB_REPOSITORY_ID = 88L;

    @BeforeAll
    void seedLinkedRepository() {
        Organization organization = organizationService.getDefaultOrganization();
        GitHubInstallation installation = gitHubInstallationRepository.save(GitHubInstallation.builder()
                .organization(organization)
                .installationId(LINKED_INSTALLATION_ID)
                .githubAccountLogin("octocat")
                .build());
        repositoryRepository.save(Repository.builder()
                .organization(organization)
                .name("core")
                .fullName("wiremock/core")
                .active(true)
                .githubRepositoryId(LINKED_GITHUB_REPOSITORY_ID)
                .githubInstallation(installation)
                .build());
    }

    @BeforeEach
    void resetStubs() {
        wireMockServer.resetAll();

        stubFor(WireMock.post(urlPathEqualTo("/app/installations/" + LINKED_INSTALLATION_ID + "/access_tokens"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"ghs_wiremock_token\",\"expires_at\":\"2099-01-01T00:00:00Z\"}")));
    }

    @Test
    void openedWebhook_runsThePolicyEngineAgainstStubbedGitHubFilesAndPersistsBothDemonstrationFindings() throws Exception {
        long githubPrId = 900_001L;
        stubFor(WireMock.get(urlPathEqualTo("/repos/wiremock/core/pulls/21/files"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("["
                                + "{\"filename\":\"src/Example.java\",\"status\":\"modified\",\"changes\":2,"
                                + "\"patch\":\"@@ -1,2 +1,3 @@\\n class Example {\\n+    // TODO: extract helper\\n+    // FIXME: null check missing\\n }\"}"
                                + "]")));

        performWebhook(payload("opened", githubPrId, 21, "sha-happy", "open", false), "delivery-happy-1")
                .andExpect(status().isOk());

        PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var run = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-happy")
                    .orElseThrow();
            assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);

            var findings = policyFindingRepository.findAll().stream()
                    .filter(f -> f.getAnalysisRun().getId().equals(run.getId()))
                    .toList();
            assertThat(findings).hasSize(2);
            assertThat(findings).extracting(f -> f.getRuleId())
                    .containsExactlyInAnyOrder("TODO_COMMENT", "FIXME_COMMENT");
        });
    }

    @Test
    void openedWebhook_completesWithNoFindingsWhenTheDiffHasNoMarkers() throws Exception {
        long githubPrId = 900_002L;
        stubFor(WireMock.get(urlPathEqualTo("/repos/wiremock/core/pulls/22/files"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("[{\"filename\":\"src/Clean.java\",\"status\":\"modified\",\"changes\":1,"
                                + "\"patch\":\"@@ -1 +1,2 @@\\n class Clean {\\n+    void ok() {}\\n }\"}]")));

        performWebhook(payload("opened", githubPrId, 22, "sha-clean", "open", false), "delivery-happy-2")
                .andExpect(status().isOk());

        PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var run = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-clean")
                    .orElseThrow();
            assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
            long findingCount = policyFindingRepository.findAll().stream()
                    .filter(f -> f.getAnalysisRun().getId().equals(run.getId()))
                    .count();
            assertThat(findingCount).isZero();
        });
    }

    private org.springframework.test.web.servlet.ResultActions performWebhook(byte[] body, String deliveryId) throws Exception {
        return mockMvc.perform(post("/api/v1/github/webhook")
                .header("X-Hub-Signature-256", sign(body))
                .header("X-GitHub-Event", "pull_request")
                .header("X-GitHub-Delivery", deliveryId)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content(body));
    }

    private byte[] payload(String action, long githubPrId, int number, String headSha, String state, boolean merged)
            throws Exception {
        PullRequestWebhookPayload payload = new PullRequestWebhookPayload(
                action,
                new PullRequestData(githubPrId, number, "Add example", new UserData("octocat"),
                        new BranchRef("feature", headSha), new BranchRef("main", "base-sha"), state, merged),
                new RepositoryData(LINKED_GITHUB_REPOSITORY_ID, "wiremock/core", "main"),
                new InstallationData(LINKED_INSTALLATION_ID));
        return objectMapper.writeValueAsBytes(payload);
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
