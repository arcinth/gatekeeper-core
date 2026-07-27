package com.gatekeeper.orchestration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
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
 * <b>{@code @Async} is required for correctness here, not an optimization</b> -
 * see {@link GitHubCheckRunPublisher#onVerdictProduced}'s Javadoc for the full
 * mechanism. This class ran synchronously until that same silent-transaction-join
 * bug was found here first: {@code EngineeringReport}/{@code AuditLog} rows were
 * being persisted into an already-committed transaction whose "commit" was a
 * no-op, with no exception anywhere.
 * <p>
 * <b>Every exception from ReportPublicationService is caught here, not
 * propagated.</b> An {@code @Async} method has no synchronous caller to
 * propagate to, but catching explicitly still gives a clearer, more specific
 * log message than {@link com.gatekeeper.config.AsyncConfig}'s generic
 * uncaught-exception handler would - and keeps a report-generation failure
 * from ever being able to affect the AnalysisRun/Verdict/AIReviewRun that
 * already committed (ADR-047), which by the time this runs, are unaffected
 * either way.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportGenerationListener {

    private final ReportPublicationService reportPublicationService;

    @Async
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

    @Async
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
