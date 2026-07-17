package com.gatekeeper.orchestration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Thin dispatcher from VerdictProducedEvent to GitHubCheckRunService - mirrors
 * ReportGenerationListener's own split from ReportPublicationService, for the
 * same reason: this listener method is called externally by Spring's event
 * multicaster, so delegating to a different bean's {@code @Transactional}
 * method here is a normal, safe proxy call, not self-invocation.
 * <p>
 * Deliberately not {@code @Async}: VerdictProducedEvent is already published
 * from an async pipeline thread (analysisExecutionTaskExecutor - see
 * VerdictProducedEvent's Javadoc), so running synchronously here costs
 * nothing and needs no third thread pool - the actual GitHub API call inside
 * GitHubCheckRunService is what takes real time, and it already happens off
 * the original request thread.
 * <p>
 * <b>Every exception from GitHubCheckRunService is caught here, not
 * propagated</b>, for the exact reason ReportGenerationListener's own Javadoc
 * documents in full: {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * registers as a {@code TransactionSynchronization.afterCommit()} callback,
 * and Spring propagates exceptions thrown from {@code afterCommit()} back to
 * the caller of the original {@code @Transactional} method. Left uncaught, a
 * GitHub API failure here could flip an already-COMPLETED AnalysisRun to
 * FAILED - exactly the cross-contamination this must not cause. The Verdict
 * and AnalysisRun this listener reacts to have already committed and are
 * unaffected either way.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubCheckRunPublisher {

    private final GitHubCheckRunService gitHubCheckRunService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerdictProduced(VerdictProducedEvent event) {
        try {
            gitHubCheckRunService.publishForVerdict(event.analysisRunId());
        } catch (RuntimeException ex) {
            log.error("GitHub check run publication failed for analysis run {} after its Verdict was produced; "
                            + "the analysis run and its Verdict are unaffected.",
                    event.analysisRunId(), ex);
        }
    }
}
