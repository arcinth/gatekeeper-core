package com.gatekeeper.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.github.GitHubInstallation;
import com.gatekeeper.github.GitHubInstallationRepository;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestRepository;
import com.gatekeeper.pullrequest.PullRequestStatus;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.repository.RepositoryRepository;
import com.gatekeeper.security.JwtService;
import com.gatekeeper.user.UserRepository;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
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
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Verifies Milestone 4's GitHub write-back for reviewer decisions end to end
 * against real Postgres and a real (WireMock-stubbed) GitHub API: recording a
 * decision must result in a "GateKeeper Review" check run being created, a
 * second decision on the same run must update that same check run rather
 * than creating a duplicate, and - the core architectural guarantee of this
 * milestone - the pre-existing Verdict-driven check run column
 * (analysis_runs.github_check_run_id) must remain untouched throughout, since
 * no Verdict is ever produced in this test.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReviewDecisionCheckRunIntegrationTest {

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

    @Value("${gatekeeper.bootstrap.admin.email}")
    private String bootstrapAdminEmail;

    private static final long LINKED_INSTALLATION_ID = 991L;

    private String bearerToken;
    private Repository repository;
    private int commitCounter = 0;

    @BeforeAll
    void seedRepositoryWithLinkedInstallation() {
        Organization organization = organizationService.getDefaultOrganization();
        GitHubInstallation installation = gitHubInstallationRepository.save(GitHubInstallation.builder()
                .organization(organization)
                .installationId(LINKED_INSTALLATION_ID)
                .githubAccountLogin("octocat")
                .build());
        repository = repositoryRepository.save(Repository.builder()
                .organization(organization)
                .name("core")
                .fullName("review-check-run-it/core")
                .owner("review-check-run-it")
                .active(true)
                .githubInstallation(installation)
                .build());
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

    /**
     * Seeds a fresh PullRequest + AnalysisRun per test method (each with its
     * own unique commit sha) rather than sharing one across the class -
     * @TestInstance(PER_CLASS) means both test methods would otherwise fight
     * over the same AnalysisRun's githubReviewCheckRunId, making the "create
     * vs. update" assertions order-dependent on which test JUnit happens to
     * run first.
     */
    private Long seedFreshAnalysisRun() {
        commitCounter++;
        String commitSha = "sha-review-check-run-it-" + commitCounter;
        PullRequest pullRequest = pullRequestRepository.save(PullRequest.builder()
                .repository(repository)
                .githubPrId(900_400L + commitCounter)
                .number(60 + commitCounter)
                .title("Add feature")
                .authorLogin("octocat")
                .sourceBranch("feature")
                .targetBranch("main")
                .headSha(commitSha)
                .status(PullRequestStatus.OPEN)
                .build());
        AnalysisRun analysisRun = analysisRunRepository.save(AnalysisRun.builder()
                .pullRequest(pullRequest)
                .commitSha(commitSha)
                .triggerReason(AnalysisRunTriggerReason.OPENED)
                .status(AnalysisRunStatus.COMPLETED)
                .build());
        return analysisRun.getId();
    }

    @Test
    void recordingADecision_publishesASeparateGateKeeperReviewCheckRunWithoutTouchingTheVerdictCheckRun() throws Exception {
        Long analysisRunId = seedFreshAnalysisRun();
        String commitSha = "sha-review-check-run-it-" + commitCounter;
        stubFor(WireMock.post(urlPathEqualTo("/repos/review-check-run-it/core/check-runs"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":88001}")));

        mockMvc.perform(post("/api/v1/analysis-runs/" + analysisRunId + "/review-decisions")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\",\"comment\":\"Looks good\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.decision").value("APPROVED"));

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                verify(postRequestedFor(urlPathEqualTo("/repos/review-check-run-it/core/check-runs"))
                        .withRequestBody(equalToJson(
                                "{"
                                        + "\"name\":\"GateKeeper Review\","
                                        + "\"head_sha\":\"" + commitSha + "\","
                                        + "\"status\":\"completed\","
                                        + "\"conclusion\":\"success\""
                                        + "}",
                                true, true))));

        AnalysisRun persisted = analysisRunRepository.findById(analysisRunId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(persisted.getGithubReviewCheckRunId()).isEqualTo(88001L);
        // The core architectural guarantee of this milestone: analysis and human
        // review are independent signals. No Verdict was ever produced for this
        // run, so the Verdict-driven check run column must remain untouched.
        org.assertj.core.api.Assertions.assertThat(persisted.getGithubCheckRunId()).isNull();
    }

    @Test
    void recordingASecondDecision_updatesTheSameCheckRunInsteadOfCreatingADuplicate() throws Exception {
        Long analysisRunId = seedFreshAnalysisRun();
        stubFor(WireMock.post(urlPathEqualTo("/repos/review-check-run-it/core/check-runs"))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":88002}")));
        stubFor(WireMock.patch(urlPathEqualTo("/repos/review-check-run-it/core/check-runs/88002"))
                .willReturn(aResponse().withStatus(200)));

        mockMvc.perform(post("/api/v1/analysis-runs/" + analysisRunId + "/review-decisions")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"APPROVED\"}"))
                .andExpect(status().isCreated());
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                verify(1, postRequestedFor(urlPathEqualTo("/repos/review-check-run-it/core/check-runs"))));

        mockMvc.perform(post("/api/v1/analysis-runs/" + analysisRunId + "/review-decisions")
                        .header("Authorization", bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"REJECTED\",\"comment\":\"Needs another pass\"}"))
                .andExpect(status().isCreated());

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                verify(patchRequestedFor(urlPathEqualTo("/repos/review-check-run-it/core/check-runs/88002"))
                        .withRequestBody(equalToJson("{\"status\":\"completed\",\"conclusion\":\"failure\"}", true, true))));

        // Still exactly one POST (create) ever - the second decision only PATCHed.
        verify(1, postRequestedFor(urlPathEqualTo("/repos/review-check-run-it/core/check-runs")));
        verify(0, deleteRequestedFor(urlPathEqualTo("/repos/review-check-run-it/core/check-runs/88002")));
    }
}
