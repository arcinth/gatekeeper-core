package com.gatekeeper.orchestration;

/**
 * Published by AnalysisOrchestrator alongside AnalysisRunReadyForExecutionEvent,
 * once an AnalysisRun is durably QUEUED. Carries only the id, for the same
 * reason as AnalysisRunReadyForExecutionEvent - see its Javadoc.
 * <p>
 * Deliberately a second, independent event rather than folding AI review into
 * AnalysisExecutionService's own execution flow: an AnalysisRun-independent
 * lifecycle means AI review starts as its own peer process, not a third
 * sequential step chained after Policy/Security - if it were a chained step,
 * an AI Review Engine failure would risk being caught by the same try/catch
 * that marks the whole AnalysisRun FAILED, exactly what Architecture.md
 * Section 3 principle 5 forbids (Sprint 4 Milestone 3).
 */
record AIReviewRequestedEvent(Long analysisRunId) {
}
