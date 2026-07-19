package com.gatekeeper.orchestration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
 * <b>{@code @Async} is required for correctness here, not an optimization.</b>
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} callbacks run from
 * inside {@code AbstractPlatformTransactionManager.triggerAfterCommit()},
 * which executes <i>before</i> that transaction manager's own cleanup unbinds
 * its {@code EntityManagerHolder} from {@code TransactionSynchronizationManager}.
 * If this method ran synchronously (on the same thread that just committed),
 * {@link GitHubCheckRunService#publishForVerdict}'s own
 * {@code @Transactional} would find that still-bound, already-committed
 * transaction and silently join it ({@code isExistingTransaction() == true})
 * instead of opening a genuinely new one. A joined (non-new) transaction's
 * "commit" is a no-op in Spring - the physical {@code doCommit()} only runs
 * for {@code isNewTransaction() == true} - so every write this method makes
 * (recording {@code githubCheckRunId}) would be silently discarded with no
 * exception anywhere: the entity stays managed, {@code save()} "succeeds",
 * and the method returns normally. {@code @Async} switches to a fresh thread
 * pool thread with no such stale resource bound, guaranteeing a real,
 * committing transaction - the exact same reason
 * {@link AnalysisExecutionService#onAnalysisRunReady} and
 * {@link com.gatekeeper.orchestration.AIReviewExecutionService}'s own
 * AFTER_COMMIT entry points are {@code @Async}. (Confirmed by direct local
 * reproduction: identical code, called synchronously from an AFTER_COMMIT
 * callback, silently failed to persist; the same call from a fresh thread
 * persisted correctly every time.)
 * <p>
 * <b>Every exception from GitHubCheckRunService is caught here, not
 * propagated</b>: an {@code @Async} method has no synchronous caller to
 * propagate to, but catching explicitly still gives a clearer, more specific
 * log message than {@link com.gatekeeper.config.AsyncConfig}'s generic
 * uncaught-exception handler would. A GitHub API failure here must never
 * affect the AnalysisRun or Verdict that already committed - both are
 * unaffected either way, since this runs well after that commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubCheckRunPublisher {

    private final GitHubCheckRunService gitHubCheckRunService;

    @Async
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
