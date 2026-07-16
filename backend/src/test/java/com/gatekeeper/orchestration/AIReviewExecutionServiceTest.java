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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.gatekeeper.aireviewengine.AIReviewContext;
import com.gatekeeper.aireviewengine.AIReviewEngineService;
import com.gatekeeper.aireviewengine.AIReviewResult;
import com.gatekeeper.aireviewengine.exception.AIProviderException;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.github.GitHubApiClient;
import com.gatekeeper.github.GitHubAppAuthService;
import com.gatekeeper.github.GitHubInstallation;
import com.gatekeeper.github.exception.GitHubApiException;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.repository.Repository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class AIReviewExecutionServiceTest {

    private static final Long ANALYSIS_RUN_ID = 1L;
    private static final long INSTALLATION_ID = 55L;
    private static final String INSTALLATION_TOKEN = "ghs_test_token";

    private final AnalysisRunService analysisRunService = mock(AnalysisRunService.class);
    private final GitHubAppAuthService gitHubAppAuthService = mock(GitHubAppAuthService.class);
    private final GitHubApiClient gitHubApiClient = mock(GitHubApiClient.class);
    private final AIReviewContextFactory aiReviewContextFactory = mock(AIReviewContextFactory.class);
    private final AIReviewEngineService aiReviewEngineService = mock(AIReviewEngineService.class);
    private final AIReviewResultPersistenceService aiReviewResultPersistenceService =
            mock(AIReviewResultPersistenceService.class);

    private AnalysisRun loadedRun;
    private AIReviewContext aiReviewContext;
    private AIReviewResult aiReviewResult;

    @BeforeEach
    void setUp() {
        Repository repository = Repository.builder()
                .fullName("gatekeeper/core")
                .githubInstallation(GitHubInstallation.builder().installationId(INSTALLATION_ID).build())
                .build();
        PullRequest pullRequest = PullRequest.builder().repository(repository).number(7).build();
        loadedRun = AnalysisRun.builder().pullRequest(pullRequest).build();
        ReflectionTestUtils.setField(loadedRun, "id", ANALYSIS_RUN_ID);

        aiReviewContext = new AIReviewContext(ANALYSIS_RUN_ID, "gatekeeper/core", 7, "t", "main", List.of());
        aiReviewResult = new AIReviewResult(ANALYSIS_RUN_ID, "anthropic-claude", "summary", List.of(), Instant.now());

        when(analysisRunService.findWithPullRequestAndRepositoryByIdOrThrow(ANALYSIS_RUN_ID)).thenReturn(loadedRun);
        when(gitHubAppAuthService.getInstallationAccessToken(INSTALLATION_ID)).thenReturn(INSTALLATION_TOKEN);
        when(gitHubApiClient.fetchPullRequestFiles(eq("gatekeeper/core"), eq(7), eq(INSTALLATION_TOKEN)))
                .thenReturn(List.of());
        when(aiReviewContextFactory.build(eq(loadedRun), any())).thenReturn(aiReviewContext);
        when(aiReviewEngineService.review(aiReviewContext)).thenReturn(aiReviewResult);
    }

    private AIReviewExecutionService serviceWith(boolean enabled) {
        return new AIReviewExecutionService(
                enabled, analysisRunService, gitHubAppAuthService, gitHubApiClient,
                aiReviewContextFactory, aiReviewEngineService, aiReviewResultPersistenceService);
    }

    @Test
    void execute_happyPath_fetchesFilesBuildsContextReviewsAndPersistsCompletedResult() {
        serviceWith(true).execute(ANALYSIS_RUN_ID);

        verify(analysisRunService).findWithPullRequestAndRepositoryByIdOrThrow(ANALYSIS_RUN_ID);
        verify(gitHubAppAuthService).getInstallationAccessToken(INSTALLATION_ID);
        verify(gitHubApiClient).fetchPullRequestFiles("gatekeeper/core", 7, INSTALLATION_TOKEN);
        verify(aiReviewEngineService).review(aiReviewContext);
        verify(aiReviewResultPersistenceService).persistCompletedResult(ANALYSIS_RUN_ID, aiReviewResult);
        verify(aiReviewResultPersistenceService, never()).persistFailedResult(anyLong(), anyString());
    }

    @Test
    void execute_fetchesChangedFilesFromGitHubExactlyOnce() {
        serviceWith(true).execute(ANALYSIS_RUN_ID);

        verify(gitHubApiClient, times(1)).fetchPullRequestFiles(anyString(), anyInt(), anyString());
    }

    @Test
    void execute_whenAnalysisRunCannotBeLoaded_doesNothingFurtherAndPersistsNothing() {
        when(analysisRunService.findWithPullRequestAndRepositoryByIdOrThrow(ANALYSIS_RUN_ID))
                .thenThrow(new RuntimeException("analysis run not found"));

        assertThatCode(() -> serviceWith(true).execute(ANALYSIS_RUN_ID)).doesNotThrowAnyException();

        verifyNoInteractions(gitHubAppAuthService, gitHubApiClient, aiReviewContextFactory, aiReviewEngineService);
        verify(aiReviewResultPersistenceService, never()).persistCompletedResult(anyLong(), any());
        verify(aiReviewResultPersistenceService, never()).persistFailedResult(anyLong(), anyString());
    }

    @Test
    void execute_whenGitHubFetchFails_persistsAFailedResultWithAGitHubApiErrorReason() {
        when(gitHubApiClient.fetchPullRequestFiles("gatekeeper/core", 7, INSTALLATION_TOKEN))
                .thenThrow(new GitHubApiException("GitHub returned 503", null));

        serviceWith(true).execute(ANALYSIS_RUN_ID);

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiReviewResultPersistenceService).persistFailedResult(eq(ANALYSIS_RUN_ID), reasonCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(reasonCaptor.getValue())
                .startsWith("GITHUB_API_ERROR")
                .contains("GitHub returned 503");
        verify(aiReviewResultPersistenceService, never()).persistCompletedResult(anyLong(), any());
    }

    @Test
    void execute_whenTheAiProviderFails_persistsAFailedResultWithAnAiProviderErrorReason() {
        when(aiReviewEngineService.review(aiReviewContext))
                .thenThrow(new AIProviderException("Anthropic API returned 500"));

        serviceWith(true).execute(ANALYSIS_RUN_ID);

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiReviewResultPersistenceService).persistFailedResult(eq(ANALYSIS_RUN_ID), reasonCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(reasonCaptor.getValue())
                .startsWith("AI_PROVIDER_ERROR")
                .contains("Anthropic API returned 500");
        verify(aiReviewResultPersistenceService, never()).persistCompletedResult(anyLong(), any());
    }

    @Test
    void execute_whenAnUnexpectedExceptionOccurs_persistsAFailedResultWithAGenericAiReviewErrorReason() {
        when(aiReviewEngineService.review(aiReviewContext)).thenThrow(new IllegalStateException("unexpected"));

        serviceWith(true).execute(ANALYSIS_RUN_ID);

        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        verify(aiReviewResultPersistenceService).persistFailedResult(eq(ANALYSIS_RUN_ID), reasonCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(reasonCaptor.getValue()).startsWith("AI_REVIEW_ERROR");
    }

    @Test
    void execute_neverTouchesAnalysisRunStatusRegardlessOfOutcome() {
        when(aiReviewEngineService.review(aiReviewContext)).thenThrow(new IllegalStateException("boom"));

        serviceWith(true).execute(ANALYSIS_RUN_ID);

        // AI review's lifecycle is independent - execute() must never call markInProgress/
        // markCompleted/markFailed, only its own read-only lookup method.
        verify(analysisRunService, never()).markInProgress(anyLong());
        verify(analysisRunService, never()).markCompleted(any());
        verify(analysisRunService, never()).markFailed(anyLong(), anyString());
    }

    @Test
    void onAIReviewRequested_whenDisabled_doesNothingAtAll() {
        serviceWith(false).onAIReviewRequested(new AIReviewRequestedEvent(ANALYSIS_RUN_ID));

        verifyNoInteractions(
                analysisRunService, gitHubAppAuthService, gitHubApiClient,
                aiReviewContextFactory, aiReviewEngineService, aiReviewResultPersistenceService);
    }

    @Test
    void onAIReviewRequested_whenEnabled_delegatesToExecute() {
        serviceWith(true).onAIReviewRequested(new AIReviewRequestedEvent(ANALYSIS_RUN_ID));

        verify(aiReviewResultPersistenceService).persistCompletedResult(ANALYSIS_RUN_ID, aiReviewResult);
    }
}
