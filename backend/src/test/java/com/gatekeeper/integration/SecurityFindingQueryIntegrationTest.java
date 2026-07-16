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
 * The Sprint 3 Milestone 3 counterpart to AnalysisRunAndPolicyFindingQueryIntegrationTest:
 * drives a run to COMPLETED through the real pipeline (both engines, proven in
 * Milestone 2), then exercises the new /api/v1/security-findings endpoints and
 * the extended /api/v1/dashboard/overview and /api/v1/analysis-runs/{id} security
 * fields with a real JWT against real Postgres.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SecurityFindingQueryIntegrationTest {

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

    private static final long LINKED_INSTALLATION_ID = 780L;
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
                .fullName("secfinding-it/core")
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
    void securityFindingEndpoints_exposeFindingsFromARunCompletedByTheRealPipeline() throws Exception {
        long githubPrId = 900_301L;
        stubFor(WireMock.get(urlPathEqualTo("/repos/secfinding-it/core/pulls/61/files"))
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

        performWebhook(payload(githubPrId, 61, "sha-secfinding-it"), "delivery-secfinding-it-1")
                .andExpect(status().isOk());

        PullRequest pullRequest = pullRequestRepository.findByGithubPrId(githubPrId).orElseThrow();
        Long analysisRunId = Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> {
            var run = analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), "sha-secfinding-it");
            return run.filter(r -> r.getStatus() == AnalysisRunStatus.COMPLETED).map(r -> r.getId()).orElse(null);
        }, id -> id != null);

        // GET /api/v1/security-findings filtered by analysisRunId
        mockMvc.perform(get("/api/v1/security-findings")
                        .param("analysisRunId", String.valueOf(analysisRunId))
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].repositoryFullName", org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.equalTo("secfinding-it/core"))));

        // GET /api/v1/security-findings filtered by repositoryId + severity
        mockMvc.perform(get("/api/v1/security-findings")
                        .param("repositoryId", String.valueOf(repositoryId))
                        .param("severity", "CRITICAL")
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].ruleId").value("HARDCODED_SECRET"));

        // Severity-sorted listing must not error and must return both rows
        mockMvc.perform(get("/api/v1/security-findings")
                        .param("analysisRunId", String.valueOf(analysisRunId))
                        .param("sort", "severity,desc")
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.data.content[1].severity").value("HIGH"));

        // GET /api/v1/security-findings/{id}
        Long firstFindingId = extractFirstFindingId(analysisRunId, bearerToken);
        mockMvc.perform(get("/api/v1/security-findings/" + firstFindingId).header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(firstFindingId));

        // GET /api/v1/security-findings/{unknown-id} -> 404
        mockMvc.perform(get("/api/v1/security-findings/999999999").header("Authorization", bearerToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GK-404"));

        // GET /api/v1/security-findings?severity=INVALID -> 400
        mockMvc.perform(get("/api/v1/security-findings?severity=NOT_REAL").header("Authorization", bearerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GK-400"));

        // GET /api/v1/analysis-runs/{id} now includes securityFindingsBySeverity
        mockMvc.perform(get("/api/v1/analysis-runs/" + analysisRunId).header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.securityFindingsBySeverity.CRITICAL").value(1))
                .andExpect(jsonPath("$.data.securityFindingsBySeverity.HIGH").value(1));

        // GET /api/v1/analysis-runs now includes securityFindingsTotal
        mockMvc.perform(get("/api/v1/analysis-runs")
                        .param("repositoryId", String.valueOf(repositoryId))
                        .header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id == " + analysisRunId + ")].securityFindingsTotal")
                        .value(2));

        // GET /api/v1/dashboard/overview reflects the real, persisted security counts
        mockMvc.perform(get("/api/v1/dashboard/overview").header("Authorization", bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSecurityFindings").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)));
    }

    private Long extractFirstFindingId(Long analysisRunId, String bearerToken) throws Exception {
        String responseBody = mockMvc.perform(get("/api/v1/security-findings")
                        .param("analysisRunId", String.valueOf(analysisRunId))
                        .header("Authorization", bearerToken))
                .andReturn().getResponse().getContentAsString();
        var tree = objectMapper.readTree(responseBody);
        return tree.get("data").get("content").get(0).get("id").asLong();
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
                new PullRequestData(githubPrId, number, "Add auth service", new UserData("octocat"),
                        new BranchRef("feature", headSha), new BranchRef("main", "base-sha"), "open", false),
                new RepositoryData(LINKED_GITHUB_REPOSITORY_ID, "secfinding-it/core", "main"),
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
