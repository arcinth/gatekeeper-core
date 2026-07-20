package com.gatekeeper.orchestration;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.github.GitHubApiClient;
import com.gatekeeper.github.GitHubAppAuthService;
import com.gatekeeper.github.dto.GitHubFileChange;
import com.gatekeeper.github.exception.GitHubApiException;
import com.gatekeeper.observability.ObservedOperation;
import com.gatekeeper.observability.OperationCategory;
import com.gatekeeper.policy.PolicyContext;
import com.gatekeeper.policy.PolicyEngineService;
import com.gatekeeper.policy.PolicyResult;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.securityengine.SecurityContext;
import com.gatekeeper.securityengine.SecurityEngineService;
import com.gatekeeper.securityengine.SecurityResult;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Runs the Policy Engine and Security Engine for a queued AnalysisRun and
 * records the outcome. Owns the execution phase of the analysis pipeline,
 * distinct from AnalysisOrchestrator's ingestion phase (Milestone 4
 * Architecture, Section 3).
 * <p>
 * As of Security Engine Architecture Section 11: the GitHub changed-files
 * fetch happens exactly once and is shared by both engines' context-building
 * steps (ADR-027) - not fetched separately per engine. Both engines run
 * sequentially on this same async thread (ADR-026), and both engines'
 * findings are gathered before the single, atomic COMPLETED transition
 * (AnalysisResultPersistenceService) - not persisted independently, since
 * AnalysisRun's status is a binary COMPLETED/FAILED state machine with no
 * partial-success state to represent "one engine succeeded, one didn't".
 * <p>
 * Runs on its own thread ({@link Async}), triggered only after the
 * ingestion transaction that created/queued the AnalysisRun has committed
 * ({@link TransactionPhase#AFTER_COMMIT}) - listening at any earlier phase
 * risks the async thread loading a row the publishing transaction hasn't
 * durably written yet (ADR-013).
 * <p>
 * Holds no transaction of its own and makes no direct repository calls: every
 * persistence step is delegated to a collaborator bean (AnalysisRunService,
 * AnalysisResultPersistenceService) specifically so their {@code @Transactional}
 * proxies are invoked from outside this class - a {@code @Transactional}
 * method here would be self-invoked by {@link #execute} and silently run
 * without a transaction at all. This is also what keeps no database
 * connection held across the GitHub API call (Section 6 / ADR-016).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisExecutionService {

    private final AnalysisRunService analysisRunService;
    private final GitHubAppAuthService gitHubAppAuthService;
    private final GitHubApiClient gitHubApiClient;
    private final PolicyContextFactory policyContextFactory;
    private final PolicyEngineService policyEngineService;
    private final SecurityContextFactory securityContextFactory;
    private final SecurityEngineService securityEngineService;
    private final AnalysisResultPersistenceService analysisResultPersistenceService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @ObservedOperation(value = "analysis.pipeline", category = OperationCategory.ANALYSIS_PIPELINE)
    public void onAnalysisRunReady(AnalysisRunReadyForExecutionEvent event) {
        execute(event.analysisRunId());
    }

    void execute(Long analysisRunId) {
        AnalysisRun analysisRun;
        try {
            analysisRun = analysisRunService.markInProgress(analysisRunId);
        } catch (RuntimeException ex) {
            // The run never left QUEUED; nothing to unwind, and nothing here
            // could reliably record a failure reason on a run we couldn't even load.
            log.error("Could not start execution for analysis run {}; it remains queued.", analysisRunId, ex);
            return;
        }

        try {
            List<GitHubFileChange> changedFiles = fetchChangedFiles(analysisRun);
            PolicyResult policyResult = runPolicyEngine(analysisRun, changedFiles);
            SecurityResult securityResult = runSecurityEngine(analysisRun, changedFiles);
            analysisResultPersistenceService.persistCompletedResult(analysisRunId, policyResult, securityResult);
        } catch (RuntimeException ex) {
            log.error("Analysis run {} failed during execution.", analysisRunId, ex);
            markFailedSafely(analysisRunId, describeFailure(ex));
        }
    }

    private List<GitHubFileChange> fetchChangedFiles(AnalysisRun analysisRun) {
        Repository repository = analysisRun.getPullRequest().getRepository();
        long installationId = repository.getGithubInstallation().getInstallationId();
        int pullRequestNumber = analysisRun.getPullRequest().getNumber();

        String installationAccessToken = gitHubAppAuthService.getInstallationAccessToken(installationId);
        return gitHubApiClient.fetchPullRequestFiles(repository.getFullName(), pullRequestNumber, installationAccessToken);
    }

    private PolicyResult runPolicyEngine(AnalysisRun analysisRun, List<GitHubFileChange> changedFiles) {
        PolicyContext context = policyContextFactory.build(analysisRun, changedFiles);
        return policyEngineService.evaluate(context);
    }

    /**
     * Wraps any failure here as SecurityEngineExecutionException so
     * describeFailure can attribute it specifically to this step rather than
     * the generic "EXECUTION_ERROR" fallback - the same triage-by-prefix
     * reasoning GitHubApiException's own dedicated label already follows.
     */
    private SecurityResult runSecurityEngine(AnalysisRun analysisRun, List<GitHubFileChange> changedFiles) {
        try {
            SecurityContext context = securityContextFactory.build(analysisRun, changedFiles);
            return securityEngineService.evaluate(context);
        } catch (RuntimeException ex) {
            throw new SecurityEngineExecutionException("Security Engine evaluation failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * A failure writing the FAILED status itself is not re-thrown: this
     * already runs from inside the async listener's own catch block, and
     * there is no further layer to escalate to. The run is left stuck at
     * IN_PROGRESS - a known, explicitly accepted gap until a future
     * reconciliation job exists (Milestone 4 Architecture, Section 7).
     */
    private void markFailedSafely(Long analysisRunId, String reason) {
        try {
            analysisRunService.markFailed(analysisRunId, reason);
        } catch (RuntimeException ex) {
            log.error("CRITICAL: could not mark analysis run {} as FAILED; it will remain stuck at IN_PROGRESS.",
                    analysisRunId, ex);
        }
    }

    /**
     * "EXECUTION_ERROR" rather than always blaming an engine: this catch-all
     * also covers auth configuration problems (GitHubAppAuthService) and
     * context-building bugs, neither of which are an engine's fault.
     * GitHubApiException and SecurityEngineExecutionException each get their
     * own, more specific label since they're actionable failure categories a
     * reader can triage on sight.
     */
    private String describeFailure(RuntimeException ex) {
        String prefix;
        if (ex instanceof GitHubApiException) {
            prefix = "GITHUB_API_ERROR";
        } else if (ex instanceof SecurityEngineExecutionException) {
            prefix = "SECURITY_ENGINE_ERROR";
        } else {
            prefix = "EXECUTION_ERROR";
        }
        String message = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        return prefix + ": " + message;
    }
}
