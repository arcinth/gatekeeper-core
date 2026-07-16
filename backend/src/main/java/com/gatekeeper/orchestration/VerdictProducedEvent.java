package com.gatekeeper.orchestration;

/**
 * Published by AnalysisResultPersistenceService immediately after a Verdict
 * is persisted and the AnalysisRun is marked COMPLETED, in the same
 * transaction (Unified Engineering Report Architecture, Section 6). One of
 * two independent triggers ReportGenerationListener joins on before a
 * report can be published - see AIReviewFinishedEvent's Javadoc for the
 * other.
 * <p>
 * Carries only the id, for the same reason as AnalysisRunReadyForExecutionEvent
 * - the AnalysisRun was loaded in the publisher's transaction and would be
 * stale/unsafe to touch from the listener's own transaction and thread.
 */
record VerdictProducedEvent(Long analysisRunId) {
}
