package com.gatekeeper.aireviewrun;

/**
 * An AIReviewRun's own terminal lifecycle state. Deliberately just two
 * values, unlike AnalysisRunStatus's five (RECEIVED/QUEUED/IN_PROGRESS/
 * COMPLETED/FAILED): an AIReviewRun row is only ever written once, after
 * execution has already finished one way or the other (Sprint 4 Milestone 3)
 * - there is no separate ingestion/queueing phase and no REST API or
 * dashboard yet to observe an in-progress intermediate state, so adding one
 * would be speculative (ADR precedent: no premature abstractions).
 */
public enum AIReviewRunStatus {
    COMPLETED,
    FAILED
}
