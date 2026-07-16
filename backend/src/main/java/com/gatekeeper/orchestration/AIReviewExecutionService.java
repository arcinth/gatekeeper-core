package com.gatekeeper.orchestration;

import com.gatekeeper.aireviewengine.AIReviewContext;
import com.gatekeeper.aireviewengine.AIReviewEngineService;
import com.gatekeeper.aireviewengine.AIReviewResult;
import com.gatekeeper.aireviewengine.exception.AIProviderException;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.github.GitHubApiClient;
import com.gatekeeper.github.GitHubAppAuthService;
import com.gatekeeper.github.dto.GitHubFileChange;
import com.gatekeeper.github.exception.GitHubApiException;
import com.gatekeeper.repository.Repository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the AI Review Engine for a queued AnalysisRun and records the
 * outcome, as its own process independent of AnalysisExecutionService (Sprint
 * 4 Milestone 3). Structurally mirrors AnalysisExecutionService - same
 * shared-fetch-then-context-then-engine-then-persist shape, same AFTER_COMMIT
 * + @Async listener pattern - but deliberately does not call, extend, or
 * share any state with it: the two are peer consumers of independent events
 * published from the same point in AnalysisOrchestrator, not a chain.
 * <p>
 * <b>Never touches AnalysisRun's own status.</b> Unlike AnalysisExecutionService,
 * this class never calls markInProgress/markCompleted/markFailed - it loads
 * the AnalysisRun read-only (AnalysisRunService#findWithPullRequestAndRepositoryByIdOrThrow)
 * and every outcome (success or failure) is recorded only on its own
 * AIReviewRun row via AIReviewResultPersistenceService. This is the concrete
 * mechanism behind "AnalysisRun-independent lifecycle": there is no code path
 * by which an AI review failure can flip an AnalysisRun to FAILED, and no
 * code path by which it blocks or delays AnalysisRun reaching COMPLETED
 * (Architecture.md Section 3 principle 5 / Section 11).
 * <p>
 * Runs on its own dedicated executor ({@code aiReviewTaskExecutor}, not
 * {@code analysisExecutionTaskExecutor}) - see AsyncConfig's Javadoc for why
 * a slower external LLM call must never compete with the deterministic
 * pipeline's own threads.
 * <p>
 * Fetches changed files independently rather than reusing the fetch
 * AnalysisExecutionService already performed for the same commit: sharing
 * that result would require threading it through the event payload (breaking
 * the established id-only event convention - see AnalysisRunReadyForExecutionEvent's
 * Javadoc) or coupling this class to AnalysisExecutionService's internals,
 * either of which reintroduces exactly the coupling "AnalysisRun-independent
 * lifecycle" is meant to avoid. The cost is a second GitHub API call per
 * AnalysisRun when AI review is enabled - an accepted, explicitly-reasoned
 * tradeoff favoring isolation over that one avoided call.
 */
@Slf4j
@Service
public class AIReviewExecutionService {

    private final boolean aiReviewEnabled;
    private final AnalysisRunService analysisRunService;
    private final GitHubAppAuthService gitHubAppAuthService;
    private final GitHubApiClient gitHubApiClient;
    private final AIReviewContextFactory aiReviewContextFactory;
    private final AIReviewEngineService aiReviewEngineService;
    private final AIReviewResultPersistenceService aiReviewResultPersistenceService;

    public AIReviewExecutionService(
            @Value("${gatekeeper.ai-review.enabled}") boolean aiReviewEnabled,
            AnalysisRunService analysisRunService,
            GitHubAppAuthService gitHubAppAuthService,
            GitHubApiClient gitHubApiClient,
            AIReviewContextFactory aiReviewContextFactory,
            AIReviewEngineService aiReviewEngineService,
            AIReviewResultPersistenceService aiReviewResultPersistenceService) {
        this.aiReviewEnabled = aiReviewEnabled;
        this.analysisRunService = analysisRunService;
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.gitHubApiClient = gitHubApiClient;
        this.aiReviewContextFactory = aiReviewContextFactory;
        this.aiReviewEngineService = aiReviewEngineService;
        this.aiReviewResultPersistenceService = aiReviewResultPersistenceService;
    }

    /**
     * Checked here, before anything is loaded or persisted, rather than
     * deferring to AnthropicAIReviewProvider's own disabled check: when AI
     * Review is off, no AIReviewRun row should be written at all - the
     * provider's own check exists for when this service calls it while
     * enabled but still misconfigured (e.g. no API key), a different case.
     */
    @Async("aiReviewTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAIReviewRequested(AIReviewRequestedEvent event) {
        if (!aiReviewEnabled) {
            log.debug("AI Review is disabled; skipping analysis run {}.", event.analysisRunId());
            return;
        }
        execute(event.analysisRunId());
    }

    void execute(Long analysisRunId) {
        AnalysisRun analysisRun;
        try {
            analysisRun = analysisRunService.findWithPullRequestAndRepositoryByIdOrThrow(analysisRunId);
        } catch (RuntimeException ex) {
            // Nothing to persist: an AIReviewRun row requires a valid analysis_run_id
            // foreign key, and we couldn't even load the run to satisfy it.
            log.error("Could not load analysis run {} for AI review; skipping.", analysisRunId, ex);
            return;
        }

        try {
            List<GitHubFileChange> changedFiles = fetchChangedFiles(analysisRun);
            AIReviewContext context = aiReviewContextFactory.build(analysisRun, changedFiles);
            AIReviewResult result = aiReviewEngineService.review(context);
            aiReviewResultPersistenceService.persistCompletedResult(analysisRunId, result);
        } catch (RuntimeException ex) {
            log.warn("AI review failed for analysis run {}; this does not affect the analysis run's own outcome.",
                    analysisRunId, ex);
            aiReviewResultPersistenceService.persistFailedResult(analysisRunId, describeFailure(ex));
        }
    }

    private List<GitHubFileChange> fetchChangedFiles(AnalysisRun analysisRun) {
        Repository repository = analysisRun.getPullRequest().getRepository();
        long installationId = repository.getGithubInstallation().getInstallationId();
        int pullRequestNumber = analysisRun.getPullRequest().getNumber();

        String installationAccessToken = gitHubAppAuthService.getInstallationAccessToken(installationId);
        return gitHubApiClient.fetchPullRequestFiles(repository.getFullName(), pullRequestNumber, installationAccessToken);
    }

    /**
     * "AI_PROVIDER_ERROR" covers both AIProviderException and its
     * AIProviderTransientException subtype - by the time this exception
     * reaches here, AnthropicAIReviewProvider's own @Retryable has already
     * exhausted its attempts, so there is no further distinction worth
     * making at this layer.
     */
    private String describeFailure(RuntimeException ex) {
        String prefix;
        if (ex instanceof AIProviderException) {
            prefix = "AI_PROVIDER_ERROR";
        } else if (ex instanceof GitHubApiException) {
            prefix = "GITHUB_API_ERROR";
        } else {
            prefix = "AI_REVIEW_ERROR";
        }
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        return prefix + ": " + message;
    }
}
