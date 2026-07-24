package com.gatekeeper.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestRepository;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.repository.RepositoryRepository;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.user.UserRepository;
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
 * Verifies Milestone 1's Pull Request read layer (GET /api/v1/pull-requests,
 * GET /api/v1/pull-requests/{id}) against real data produced by the real
 * pipeline, the same way AnalysisRunAndPolicyFindingQueryIntegrationTest
 * verifies the analysis-run read layer - specifically to catch the
 * fetch-join + Specification + pagination + batched-enrichment interaction a
 * unit test with mocked repositories cannot prove.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PullRequestQueryIntegrationTest {

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
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Value("${gatekeeper.github.webhook.secret}")
    private String webhookSecret;

    @Value("${gatekeeper.bootstrap.admin.email}")
    private String bootstrapAdminEmail;

    private static final long LINKED_INSTALLATION_ID = 881L;
    private static final long LINKED_GITHUB_REPOSITORY_ID = 91L;

    private String bearerToken;
    private Long repositoryId;

    @BeforeAll
    void seedLinkedRepository() {
        Organization organization = organizationService.getDefaultOrganization();
        GitHubInstallation installation = gitHubInstallationRepository.save(GitHubInstallation.builder()
                .organization(organization)
                .installationId(LINKED_INSTALLATION_ID)
                .githubAccountLogin("octocat")
                .build());
        Repository repository = repositoryRepository.save(Repository.builder()
                .organization(organization)
                .name("core")
                .fullName("pr-query-it/core")
                .owner("pr-query-it")
                .active(true)
                .githubRepositoryId(LINKED_GITHUB_REPOSITORY_ID)
                .githubInstallation(installation)
                .build());
        repositoryId = repository.getId();
    }

    @BeforeEach
    void resetStubsAndAuthenticate() {
        wireMockServer.resetAll();

        stubFor(WireMock.post(urlPathEqualTo("/app/installations/" + LINKED_INSTALLATION_ID + "/access_tokens"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"ghs_wiremock_token\",\"expires_at\":\"2099-01-01T00:00:00Z\"}")));

        var admin = userRepository.findByEmailIgnoreCase(bootstrapAdminEmail).orElseThrow();
        bearerToken = "Bearer " + jwtService.generateAccessToken(
                admin.getId(), admin.getEmail(), admin.getRole().getName(), admin.getOrganization().getId());
    }

    @Test
    void pullRequestEndpoints_exposeAPullRequestCreatedByTheRealPipelineWithItsAnalysisHistory() throws Exception {
        long githubPrId = 900_201L;
        stubFor(WireMock.get(urlPathEqualTo("/repos/pr-query-it/core/pulls/44/files"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("[{\"filename\":\"src/Example.java\",\"status\":\"modified\",\"changes\":1,"
                                + "\"patch\":\"@@ -1 +1,2 @@\\n class Example {\\n+    // TODO: wire this up\\n }\"}]")));

        performWebhook(payload(githubPrId, 44, "sha-pr-query-it"), "delivery-pr-query-it-1")
                .andExpect(status().isOk());

        PullRequest pullRequest = Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> pullRequestRepository.findByGithubPrId(githubPrId).orElse(null), java.util.Objects::nonNull);
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            var run = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-pr-query-it");
            return run.filter(r -> r.getStatus() == AnalysisRunStatus.COMPLETED).isPresent();
        });

        // List view: filtered by repositoryId, denormalized GitHub metadata and latest-run enrichment
        // from the fetch-joined query plus the two batched supplementary queries.
        mockMvc.perform(get("/api/v1/pull-requests")
                        .param("repositoryId", String.valueOf(repositoryId))
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + pullRequest.getId() + ")].number").value(44))
                .andExpect(jsonPath("$.data.content[?(@.id == " + pullRequest.getId() + ")].repositoryOwner")
                        .value("pr-query-it"))
                .andExpect(jsonPath("$.data.content[?(@.id == " + pullRequest.getId() + ")].repositoryName")
                        .value("core"))
                .andExpect(jsonPath("$.data.content[?(@.id == " + pullRequest.getId() + ")].githubUrl")
                        .value("https://github.com/pr-query-it/core/pull/44"))
                .andExpect(jsonPath("$.data.content[?(@.id == " + pullRequest.getId() + ")].latestAnalysisRunStatus")
                        .value("COMPLETED"));

        // Status filter narrows correctly.
        mockMvc.perform(get("/api/v1/pull-requests")
                        .param("repositoryId", String.valueOf(repositoryId))
                        .param("status", "OPEN")
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + pullRequest.getId() + ")].id")
                        .value(pullRequest.getId().intValue()));

        // Detail view: full analysis-run history for this PR, with GitHub metadata.
        mockMvc.perform(get("/api/v1/pull-requests/" + pullRequest.getId()).header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.number").value(44))
                .andExpect(jsonPath("$.data.repository.owner").value("pr-query-it"))
                .andExpect(jsonPath("$.data.repository.name").value("core"))
                .andExpect(jsonPath("$.data.githubUrl").value("https://github.com/pr-query-it/core/pull/44"))
                .andExpect(jsonPath("$.data.analysisRuns[0].commitSha").value("sha-pr-query-it"))
                .andExpect(jsonPath("$.data.analysisRuns[0].status").value("COMPLETED"));

        // The Analysis Run detail endpoint now carries pullRequestId, closing the cross-link loop.
        var analysisRun = analysisRunRepository
                .findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-pr-query-it").orElseThrow();
        mockMvc.perform(get("/api/v1/analysis-runs/" + analysisRun.getId()).header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pullRequestId").value(pullRequest.getId().intValue()));
    }

    @Test
    void pullRequestDetail_returns404ForAnUnknownId() throws Exception {
        mockMvc.perform(get("/api/v1/pull-requests/999999999").header("Authorization", bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }

    @Test
    void pullRequestList_returns400ForAnInvalidStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/pull-requests").param("status", "NOT_REAL").header("Authorization", bearerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GK-400"));
    }

    private org.springframework.test.web.servlet.ResultActions performWebhook(byte[] body, String deliveryId)
            throws Exception {
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
                new RepositoryData(LINKED_GITHUB_REPOSITORY_ID, "pr-query-it/core", "main"),
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
