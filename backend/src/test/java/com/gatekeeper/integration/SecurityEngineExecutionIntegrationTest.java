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
import com.gatekeeper.securityfinding.SecurityFindingRepository;
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
 * The Sprint 3 Milestone 2 counterpart to PolicyEngineExecutionIntegrationTest:
 * proves the orchestration integration end to end against a real Postgres
 * instance - both engines run from one shared GitHub fetch, and both engines'
 * findings land atomically alongside the single COMPLETED transition
 * (AnalysisResultPersistenceService). No REST assertions here since the
 * read/query API for security findings doesn't exist yet (a later milestone
 * of this sprint) - findings are asserted directly via SecurityFindingRepository
 * and PolicyFindingRepository instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityEngineExecutionIntegrationTest {

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
        // WireMock.stubFor(...) below (and elsewhere in this class) is the
        // static DSL, which talks to an internal admin client that defaults
        // to localhost:8080 unless told which port wireMockServer is
        // actually running on.
        WireMock.configureFor(wireMockServer.port());
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("gatekeeper.github.api.base-url", () -> wireMockServer.baseUrl());
        registry.add("gatekeeper.github.app.id", () -> "12345");
        registry.add("gatekeeper.github.app.private-key", () -> testAppPrivateKeyPem);
        // Must not be left at the committed placeholder default alongside a
        // real (non-zero) app.id above - GitHubAppConfigurationDiagnostics
        // refuses to start the context otherwise (Milestone 10 follow-up:
        // this exact combination is what a genuinely misconfigured deployment
        // looks like, so the check does not carve out an exception for tests).
        registry.add("gatekeeper.github.webhook.secret", () -> "test-webhook-secret-for-integration-tests");
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

    @Autowired
    private SecurityFindingRepository securityFindingRepository;

    @Value("${gatekeeper.github.webhook.secret}")
    private String webhookSecret;

    private static final long LINKED_INSTALLATION_ID = 779L;
    private static final long LINKED_GITHUB_REPOSITORY_ID = 90L;

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
                .fullName("sec-it/core")
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
    void openedWebhook_runsBothEnginesFromOneSharedFetchAndPersistsBothEnginesFindingsAtomically() throws Exception {
        long githubPrId = 900_201L;
        stubFor(WireMock.get(urlPathEqualTo("/repos/sec-it/core/pulls/41/files"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("["
                                + "{\"filename\":\"src/AuthService.java\",\"status\":\"modified\",\"changes\":4,"
                                + "\"patch\":\"@@ -1,2 +1,6 @@\\n class AuthService {\\n"
                                + "+    // TODO: extract helper\\n"
                                + "+    // FIXME: null check missing\\n"
                                + "+    String apiKey = \\\"sk-live-abcdef1234567890\\\";\\n"
                                + "+    MessageDigest md = MessageDigest.getInstance(\\\"MD5\\\");\\n"
                                + " }\"}"
                                + "]")));

        performWebhook(payload(githubPrId, 41, "sha-sec-it"), "delivery-sec-it-1").andExpect(status().isOk());

        PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var run = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-sec-it")
                    .orElseThrow();
            assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);

            var policyFindings = policyFindingRepository.findAll().stream()
                    .filter(f -> f.getAnalysisRun().getId().equals(run.getId()))
                    .toList();
            assertThat(policyFindings).hasSize(2);
            assertThat(policyFindings).extracting(f -> f.getRuleId())
                    .containsExactlyInAnyOrder("TODO_COMMENT", "FIXME_COMMENT");

            var securityFindings = securityFindingRepository.findAll().stream()
                    .filter(f -> f.getAnalysisRun().getId().equals(run.getId()))
                    .toList();
            assertThat(securityFindings).hasSize(2);
            assertThat(securityFindings).extracting(f -> f.getRuleId())
                    .containsExactlyInAnyOrder("HARDCODED_SECRET", "INSECURE_CRYPTO_FUNCTION");
        });

        // ADR-027: exactly one GitHub files fetch for the whole run, shared by both engines -
        // not one per engine. Page 1 only, since the stub returns fewer than 100 files.
        wireMockServer.verify(1, getRequestedFor(urlPathEqualTo("/repos/sec-it/core/pulls/41/files")));
        wireMockServer.verify(1, postRequestedFor(urlPathEqualTo(
                "/app/installations/" + LINKED_INSTALLATION_ID + "/access_tokens")));
    }

    @Test
    void openedWebhook_completesWithNoFindingsFromEitherEngineWhenTheDiffIsClean() throws Exception {
        long githubPrId = 900_202L;
        stubFor(WireMock.get(urlPathEqualTo("/repos/sec-it/core/pulls/42/files"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("[{\"filename\":\"src/Clean.java\",\"status\":\"modified\",\"changes\":1,"
                                + "\"patch\":\"@@ -1 +1,2 @@\\n class Clean {\\n+    void ok() {}\\n }\"}]")));

        performWebhook(payload(githubPrId, 42, "sha-sec-it-clean"), "delivery-sec-it-2").andExpect(status().isOk());

        PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var run = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-sec-it-clean")
                    .orElseThrow();
            assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);

            long policyCount = policyFindingRepository.findAll().stream()
                    .filter(f -> f.getAnalysisRun().getId().equals(run.getId())).count();
            long securityCount = securityFindingRepository.findAll().stream()
                    .filter(f -> f.getAnalysisRun().getId().equals(run.getId())).count();
            assertThat(policyCount).isZero();
            assertThat(securityCount).isZero();
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

    private byte[] payload(long githubPrId, int number, String headSha) throws Exception {
        PullRequestWebhookPayload payload = new PullRequestWebhookPayload(
                "opened",
                new PullRequestData(githubPrId, number, "Add example", new UserData("octocat"),
                        new BranchRef("feature", headSha), new BranchRef("main", "base-sha"), "open", false),
                new RepositoryData(LINKED_GITHUB_REPOSITORY_ID, "sec-it/core", "main"),
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
