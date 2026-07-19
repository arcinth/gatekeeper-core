package com.gatekeeper.orchestration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.github.GitHubApiClient;
import com.gatekeeper.github.GitHubAppAuthService;
import com.gatekeeper.github.GitHubInstallation;
import com.gatekeeper.github.dto.GitHubFileChange;
import com.gatekeeper.github.exception.GitHubApiException;
import com.gatekeeper.policy.PolicyContext;
import com.gatekeeper.policy.PolicyEngineService;
import com.gatekeeper.policy.PolicyResult;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.securityengine.SecurityContext;
import com.gatekeeper.securityengine.SecurityEngineService;
import com.gatekeeper.securityengine.SecurityResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AnalysisExecutionServiceTest {

    private static final Long ANALYSIS_RUN_ID = 1L;
    private static final long INSTALLATION_ID = 55L;
    private static final String INSTALLATION_TOKEN = "ghs_test_token";

    private final AnalysisRunService analysisRunService = mock(AnalysisRunService.class);
    private final GitHubAppAuthService gitHubAppAuthService = mock(GitHubAppAuthService.class);
    private final GitHubApiClient gitHubApiClient = mock(GitHubApiClient.class);
    private final PolicyContextFactory policyContextFactory = mock(PolicyContextFactory.class);
    private final PolicyEngineService policyEngineService = mock(PolicyEngineService.class);
    private final SecurityContextFactory securityContextFactory = mock(SecurityContextFactory.class);
    private final SecurityEngineService securityEngineService = mock(SecurityEngineService.class);
    private final AnalysisResultPersistenceService analysisResultPersistenceService =
            mock(AnalysisResultPersistenceService.class);

    private final AnalysisExecutionService service = new AnalysisExecutionService(
            analysisRunService, gitHubAppAuthService, gitHubApiClient,
            policyContextFactory, policyEngineService,
            securityContextFactory, securityEngineService, analysisResultPersistenceService);

    private AnalysisRun inProgressRun;
    private PolicyContext policyContext;
    private PolicyResult policyResult;
    private SecurityContext securityContext;
    private SecurityResult securityResult;

    @BeforeEach
    void setUp() {
        Repository repository = Repository.builder()
                .fullName("gatekeeper/core")
                .githubInstallation(GitHubInstallation.builder().installationId(INSTALLATION_ID).build())
                .build();
        PullRequest pullRequest = PullRequest.builder().repository(repository).number(7).build();
        inProgressRun = AnalysisRun.builder().pullRequest(pullRequest).build();
        ReflectionTestUtils.setField(inProgressRun, "id", ANALYSIS_RUN_ID);

        policyContext = new PolicyContext(ANALYSIS_RUN_ID, 1L, "gatekeeper/core", List.of());
        policyResult = new PolicyResult(ANALYSIS_RUN_ID, List.of(), 2, Instant.now());
        securityContext = new SecurityContext(ANALYSIS_RUN_ID, "gatekeeper/core", List.of());
        securityResult = new SecurityResult(ANALYSIS_RUN_ID, List.of(), 2, Instant.now());

        when(analysisRunService.markInProgress(ANALYSIS_RUN_ID)).thenReturn(inProgressRun);
        when(gitHubAppAuthService.getInstallationAccessToken(INSTALLATION_ID)).thenReturn(INSTALLATION_TOKEN);
        when(gitHubApiClient.fetchPullRequestFiles(eq("gatekeeper/core"), eq(7), eq(INSTALLATION_TOKEN)))
                .thenReturn(List.of());
        when(policyContextFactory.build(eq(inProgressRun), any())).thenReturn(policyContext);
        when(policyEngineService.evaluate(policyContext)).thenReturn(policyResult);
        when(securityContextFactory.build(eq(inProgressRun), any())).thenReturn(securityContext);
        when(securityEngineService.evaluate(securityContext)).thenReturn(securityResult);
    }

    @Test
    void execute_happyPath_runsEachStageInOrderAndPersistsBothResultsAtomically() {
        service.execute(ANALYSIS_RUN_ID);

        verify(analysisRunService).markInProgress(ANALYSIS_RUN_ID);
        verify(gitHubAppAuthService).getInstallationAccessToken(INSTALLATION_ID);
        verify(gitHubApiClient).fetchPullRequestFiles("gatekeeper/core", 7, INSTALLATION_TOKEN);
        verify(policyEngineService).evaluate(policyContext);
        verify(securityEngineService).evaluate(securityContext);
        verify(analysisResultPersistenceService)
                .persistCompletedResult(ANALYSIS_RUN_ID, policyResult, securityResult);
        verify(analysisRunService, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void execute_fetchesChangedFilesFromGitHubExactlyOnceEvenThoughTwoEnginesConsumeTheResult() {
        service.execute(ANALYSIS_RUN_ID);

        // Milestone 2's core optimization: one GitHub call shared by both engines'
        // context-building steps, not one call per engine (ADR-027). This is the
        // test most likely to silently regress if a future change reintroduces a
        // second fetch.
        verify(gitHubApiClient, times(1)).fetchPullRequestFiles(anyString(), anyInt(), anyString());
        verify(gitHubAppAuthService, times(1)).getInstallationAccessToken(INSTALLATION_ID);
    }

    @Test
    void execute_passesTheSameChangedFilesFromGitHubIntoBothContextFactories() {
        List<GitHubFileChange> changedFiles = List.of(new GitHubFileChange("a.txt", "modified", 1, "+line"));
        when(gitHubApiClient.fetchPullRequestFiles("gatekeeper/core", 7, INSTALLATION_TOKEN)).thenReturn(changedFiles);

        service.execute(ANALYSIS_RUN_ID);

        verify(policyContextFactory).build(inProgressRun, changedFiles);
        verify(securityContextFactory).build(inProgressRun, changedFiles);
    }

    @Test
    void execute_whenMarkInProgressFails_doesNotAttemptToMarkFailed() {
        when(analysisRunService.markInProgress(ANALYSIS_RUN_ID))
                .thenThrow(new RuntimeException("analysis run not found"));

        assertThatCode(() -> service.execute(ANALYSIS_RUN_ID)).doesNotThrowAnyException();

        verify(analysisRunService, never()).markFailed(anyLong(), anyString());
        verify(gitHubAppAuthService, never()).getInstallationAccessToken(anyLong());
    }

    @Test
    void execute_whenGitHubAuthFails_marksTheRunFailedWithAGitHubApiErrorReason() {
        when(gitHubAppAuthService.getInstallationAccessToken(INSTALLATION_ID))
                .thenThrow(new GitHubApiException("installation revoked", null));

        service.execute(ANALYSIS_RUN_ID);

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(analysisRunService).markFailed(eq(ANALYSIS_RUN_ID), reasonCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(reasonCaptor.getValue())
                .startsWith("GITHUB_API_ERROR")
                .contains("installation revoked");
        verify(analysisResultPersistenceService, never()).persistCompletedResult(anyLong(), any(), any());
    }

    @Test
    void execute_whenFetchingChangedFilesFails_marksTheRunFailed() {
        when(gitHubApiClient.fetchPullRequestFiles("gatekeeper/core", 7, INSTALLATION_TOKEN))
                .thenThrow(new GitHubApiException("GitHub returned 503", null));

        service.execute(ANALYSIS_RUN_ID);

        verify(analysisRunService).markFailed(eq(ANALYSIS_RUN_ID), anyString());
        verify(analysisResultPersistenceService, never()).persistCompletedResult(anyLong(), any(), any());
    }

    @Test
    void execute_whenPolicyEngineThrowsUnexpectedly_marksTheRunFailedWithAnExecutionErrorReason() {
        when(policyEngineService.evaluate(policyContext)).thenThrow(new IllegalStateException("unexpected"));

        service.execute(ANALYSIS_RUN_ID);

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(analysisRunService).markFailed(eq(ANALYSIS_RUN_ID), reasonCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(reasonCaptor.getValue()).startsWith("EXECUTION_ERROR");
        verify(securityEngineService, never()).evaluate(any());
    }

    @Test
    void execute_whenSecurityEngineThrowsUnexpectedly_marksTheRunFailedWithASecurityEngineErrorReason() {
        when(securityEngineService.evaluate(securityContext)).thenThrow(new IllegalStateException("unexpected"));

        service.execute(ANALYSIS_RUN_ID);

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(analysisRunService).markFailed(eq(ANALYSIS_RUN_ID), reasonCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(reasonCaptor.getValue())
                .startsWith("SECURITY_ENGINE_ERROR")
                .contains("unexpected");
        verify(analysisResultPersistenceService, never()).persistCompletedResult(anyLong(), any(), any());
    }

    @Test
    void execute_whenSecurityEngineFailsPolicyFindingsAreStillNotPersisted_becauseCompletionIsAtomicAcrossBothEngines() {
        when(securityEngineService.evaluate(securityContext)).thenThrow(new IllegalStateException("boom"));

        service.execute(ANALYSIS_RUN_ID);

        // Policy Engine already succeeded by this point, but persistence never happens for
        // either engine's findings unless BOTH engines succeeded - no partial writes.
        verify(analysisResultPersistenceService, never()).persistCompletedResult(anyLong(), any(), any());
    }

    @Test
    void execute_whenPersistingFindingsFails_stillAttemptsToMarkTheRunFailed() {
        org.mockito.Mockito.doThrow(new RuntimeException("db error"))
                .when(analysisResultPersistenceService)
                .persistCompletedResult(ANALYSIS_RUN_ID, policyResult, securityResult);

        assertThatCode(() -> service.execute(ANALYSIS_RUN_ID)).doesNotThrowAnyException();

        verify(analysisRunService).markFailed(eq(ANALYSIS_RUN_ID), anyString());
    }

    @Test
    void execute_whenMarkingFailedAlsoFails_doesNotPropagateAndLeavesTheRunAsIs() {
        when(gitHubAppAuthService.getInstallationAccessToken(INSTALLATION_ID))
                .thenThrow(new GitHubApiException("boom", null));
        org.mockito.Mockito.doThrow(new RuntimeException("db unavailable"))
                .when(analysisRunService).markFailed(eq(ANALYSIS_RUN_ID), anyString());

        assertThatCode(() -> service.execute(ANALYSIS_RUN_ID)).doesNotThrowAnyException();
    }
}
