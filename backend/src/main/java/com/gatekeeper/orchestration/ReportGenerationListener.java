package com.gatekeeper.orchestration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Thin dispatcher from VerdictProducedEvent/AIReviewFinishedEvent to
 * ReportPublicationService (Unified Engineering Report Architecture, Section
 * 6) - mirrors AnalysisExecutionService's and AIReviewExecutionService's own
 * split from their respective *PersistenceService, for the same Spring AOP
 * reason: these listener methods are called externally by Spring's event
 * multicaster, so delegating to a different bean's {@code @Transactional}
 * method here is a normal, safe proxy call, not self-invocation.
 * <p>
 * Deliberately not {@code @Async}: both events are already published from an
 * async pipeline thread (analysisExecutionTaskExecutor or aiReviewTaskExecutor
 * respectively - see VerdictProducedEvent/AIReviewFinishedEvent's Javadoc for
 * where), and report publication is pure DB work with no external I/O, so
 * running synchronously on that already-async thread costs nothing and needs
 * no third thread pool.
 * <p>
 * <b>Every exception from ReportPublicationService is caught here, not
 * propagated.</b> This is not optional hygiene: {@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * registers as a Spring {@code TransactionSynchronization.afterCommit()}
 * callback, and Spring's transaction manager deliberately propagates
 * exceptions thrown from {@code afterCommit()} back to the caller of the
 * original {@code @Transactional} method (unlike {@code afterCompletion()},
 * which it always swallows-and-logs). Left uncaught, a bug in report
 * publication would surface inside AnalysisExecutionService.execute()'s or
 * AIReviewExecutionService.execute()'s own try/catch and could flip an
 * already-COMPLETED AnalysisRun to FAILED, or write a spurious second
 * AIReviewRun row - exactly the cross-contamination ADR-047 forbids. Catching
 * here is what actually enforces "a report-generation failure must never
 * affect anything that already committed" at the framework level.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationListener {

    private final ReportPublicationService reportPublicationService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onVerdictProduced(VerdictProducedEvent event) {
        try {
            reportPublicationService.onVerdictProduced(event.analysisRunId());
        } catch (RuntimeException ex) {
            log.error("Report publication check failed for analysis run {} after its Verdict was produced; "
                            + "the analysis run and its Verdict are unaffected - a future timeout sweep will retry.",
                    event.analysisRunId(), ex);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAiReviewFinished(AIReviewFinishedEvent event) {
        try {
            reportPublicationService.onAiReviewFinished(event.analysisRunId(), event.status());
        } catch (RuntimeException ex) {
            log.error("Report publication check failed for analysis run {} after AI review finished; "
                            + "the AI review run's own outcome is unaffected.",
                    event.analysisRunId(), ex);
        }
    }
}
