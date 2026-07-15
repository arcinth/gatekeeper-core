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
 * Verifies Milestone 5's read layer against real data produced by the real
 * pipeline (webhook -> async execution -> Policy Engine -> persisted
 * findings, proven end-to-end in PolicyEngineExecutionIntegrationTest), then
 * exercises the new GET endpoints with a real JWT - specifically to catch the
 * fetch-join + Specification + pagination interaction (Section 8) that a unit
 * test with mocked repositories cannot prove, since that behavior only
 * manifests against a real JPA provider and a real database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
class AnalysisRunAndPolicyFindingQueryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static WireMockServer wireMockServer;
    private static String testAppPrivateKeyPem;

    @BeforeAll
    static void startWireMockAndGenerateTestKey() throws Exception {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();

        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(keyPair.getPrivate().getEncoded());
        testAppPrivateKeyPem = "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }

    @AfterAll
    static void stopWireMock() {
        wireMockServer.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
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
    private UserRepository userRepository;

    @Autowired
    private JwtService jwtService;

    @Value("${gatekeeper.github.webhook.secret}")
    private String webhookSecret;

    @Value("${gatekeeper.bootstrap.admin.email}")
    private String bootstrapAdminEmail;

    private static final long LINKED_INSTALLATION_ID = 778L;
    private static final long LINKED_GITHUB_REPOSITORY_ID = 89L;

    private String bearerToken;
    private Long repositoryId;

    @BeforeEach
    void seedLinkedRepositoryAndAuthentication() {
        wireMockServer.resetAll();

        Organization organization = organizationService.getDefaultOrganization();
        GitHubInstallation installation = gitHubInstallationRepository.save(GitHubInstallation.builder()
                .organization(organization)
                .installationId(LINKED_INSTALLATION_ID)
                .githubAccountLogin("octocat")
                .build());
        Repository repository = repositoryRepository.save(Repository.builder()
                .organization(organization)
                .name("core")
                .fullName("query-it/core")
                .active(true)
                .githubRepositoryId(LINKED_GITHUB_REPOSITORY_ID)
                .githubInstallation(installation)
                .build());
        repositoryId = repository.getId();

        stubFor(WireMock.post(urlPathEqualTo("/app/installations/" + LINKED_INSTALLATION_ID + "/access_tokens"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"token\":\"ghs_wiremock_token\",\"expires_at\":\"2099-01-01T00:00:00Z\"}")));

        var admin = userRepository.findByEmailIgnoreCase(bootstrapAdminEmail).orElseThrow();
        bearerToken = "Bearer " + jwtService.generateAccessToken(
                admin.getId(), admin.getEmail(), admin.getRole().getName(), admin.getOrganization().getId());
    }

    @Test
    void queryEndpoints_exposeARunCompletedByTheRealPipelineWithItsFindings() throws Exception {
        long githubPrId = 900_101L;
        stubFor(WireMock.get(urlPathEqualTo("/repos/query-it/core/pulls/31/files"))
                .withQueryParam("page", equalTo("1"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("["
                                + "{\"filename\":\"src/Example.java\",\"status\":\"modified\",\"changes\":2,"
                                + "\"patch\":\"@@ -1,2 +1,3 @@\\n class Example {\\n+    // TODO: extract helper\\n+    // FIXME: null check missing\\n }\"}"
                                + "]")));

        performWebhook(payload(githubPrId, 31, "sha-query-it"), "delivery-query-it-1").andExpect(status().isOk());

        PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();
        Long analysisRunId = Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            var run = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-query-it");
            return run.filter(r -> r.getStatus() == AnalysisRunStatus.COMPLETED).map(r -> r.getId()).orElse(null);
        }, id -> id != null);

        // List view: filtered by repositoryId and status, denormalized fields from the fetch-joined query, findingsTotal from the batch enrichment query.
        mockMvc.perform(get("/api/v1/analysis-runs")
                        .param("repositoryId", String.valueOf(repositoryId))
                        .param("status", "COMPLETED")
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + analysisRunId + ")].repositoryFullName")
                        .value("query-it/core"))
                .andExpect(jsonPath("$.data.content[?(@.id == " + analysisRunId + ")].findingsTotal").value(2));

        // Detail view: findings-by-severity breakdown for exactly this run.
        mockMvc.perform(get("/api/v1/analysis-runs/" + analysisRunId).header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repository.fullName").value("query-it/core"))
                .andExpect(jsonPath("$.data.pullRequest.number").value(31))
                .andExpect(jsonPath("$.data.findingsBySeverity.LOW").value(2));

        // Flat cross-run findings listing, filtered by this run's id.
        mockMvc.perform(get("/api/v1/policy-findings")
                        .param("analysisRunId", String.valueOf(analysisRunId))
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].repositoryFullName", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo("query-it/core"))));

        // Severity-sorted findings listing must not fall back to alphabetical ordering (CRITICAL before HIGH,
        // never before LOW): with only LOW findings present here, this exercises the sort path without erroring.
        mockMvc.perform(get("/api/v1/policy-findings")
                        .param("analysisRunId", String.valueOf(analysisRunId))
                        .param("sort", "severity,desc")
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));

        // Dashboard overview reflects the real, persisted counts.
        mockMvc.perform(get("/api/v1/dashboard/overview").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalFindings").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    @Test
    void analysisRunDetail_returns404ForAnUnknownId() throws Exception {
        var admin = userRepository.findByEmailIgnoreCase(bootstrapAdminEmail).orElseThrow();
        String token = "Bearer " + jwtService.generateAccessToken(
                admin.getId(), admin.getEmail(), admin.getRole().getName(), admin.getOrganization().getId());

        mockMvc.perform(get("/api/v1/analysis-runs/999999999").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));
    }

    @Test
    void analysisRunList_returns400ForAnInvalidStatusFilter() throws Exception {
        mockMvc.perform(get("/api/v1/analysis-runs").param("status", "NOT_REAL").header("Authorization", bearerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GK-400"));
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
                new RepositoryData(LINKED_GITHUB_REPOSITORY_ID, "query-it/core", "main"),
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
