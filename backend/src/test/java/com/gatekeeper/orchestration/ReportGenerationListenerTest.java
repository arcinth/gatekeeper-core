package com.gatekeeper.orchestration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import org.junit.jupiter.api.Test;

class ReportGenerationListenerTest {

    private static final Long ANALYSIS_RUN_ID = 1L;

    private final ReportPublicationService reportPublicationService = mock(ReportPublicationService.class);
    private final ReportGenerationListener listener = new ReportGenerationListener(reportPublicationService);

    @Test
    void onVerdictProduced_delegatesToThePublicationServiceWithTheEventsAnalysisRunId() {
        listener.onVerdictProduced(new VerdictProducedEvent(ANALYSIS_RUN_ID));

        verify(reportPublicationService).onVerdictProduced(ANALYSIS_RUN_ID);
    }

    @Test
    void onAiReviewFinished_delegatesToThePublicationServiceWithTheEventsAnalysisRunIdAndStatus() {
        listener.onAiReviewFinished(new AIReviewFinishedEvent(ANALYSIS_RUN_ID, AIReviewRunStatus.COMPLETED));

        verify(reportPublicationService).onAiReviewFinished(ANALYSIS_RUN_ID, AIReviewRunStatus.COMPLETED);
    }

    /**
     * The load-bearing safety test (see this class's own Javadoc): a
     * @TransactionalEventListener(phase = AFTER_COMMIT) method's exceptions
     * are propagated by Spring back to the caller of the original
     * @Transactional method, unlike afterCompletion()'s. Left uncaught, a
     * Report Engine bug here would surface inside AnalysisExecutionService's
     * own try/catch and could flip an already-COMPLETED AnalysisRun to
     * FAILED. This proves that can never happen.
     */
    @Test
    void onVerdictProduced_neverPropagatesAnExceptionFromThePublicationService() {
        doThrow(new IllegalStateException("simulated report engine bug"))
                .when(reportPublicationService).onVerdictProduced(eq(ANALYSIS_RUN_ID));

        assertThatCode(() -> listener.onVerdictProduced(new VerdictProducedEvent(ANALYSIS_RUN_ID)))
                .doesNotThrowAnyException();
    }

    @Test
    void onAiReviewFinished_neverPropagatesAnExceptionFromThePublicationService() {
        doThrow(new IllegalStateException("simulated report engine bug"))
                .when(reportPublicationService).onAiReviewFinished(eq(ANALYSIS_RUN_ID), eq(AIReviewRunStatus.FAILED));

        assertThatCode(() -> listener.onAiReviewFinished(new AIReviewFinishedEvent(ANALYSIS_RUN_ID, AIReviewRunStatus.FAILED)))
                .doesNotThrowAnyException();
    }
}
