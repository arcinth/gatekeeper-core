package com.gatekeeper.analysisrun;

/**
 * The full conceptual lifecycle from Sprint 2 Architecture, Section 9 - modeled
 * now so a future milestone that wires up the first AnalysisEngine needs no
 * schema change, only new code that transitions into these states. Milestone 2
 * never produces anything past RECEIVED: no AnalysisEngine exists yet to run.
 */
public enum AnalysisRunStatus {
    RECEIVED,
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
