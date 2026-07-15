package com.gatekeeper.orchestration;

/**
 * Published by AnalysisOrchestrator once an AnalysisRun is durably QUEUED.
 * Carries only the id, never the AnalysisRun entity itself - the entity was
 * loaded in the publisher's transaction and would be stale/unsafe to touch
 * from the listener's own transaction and thread (Milestone 4 Architecture,
 * Section 3).
 */
record AnalysisRunReadyForExecutionEvent(Long analysisRunId) {
}
