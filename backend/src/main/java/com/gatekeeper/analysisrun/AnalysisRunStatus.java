package com.gatekeeper.analysisrun;

/**
 * The full conceptual lifecycle from Sprint 2 Architecture, Section 9 - modeled
 * in Milestone 2 so this moment (Milestone 4, wiring up the Policy Engine)
 * needed no schema change, only new code that transitions into these states:
 * RECEIVED -> QUEUED (AnalysisOrchestrator) -> IN_PROGRESS -> COMPLETED/FAILED
 * (AnalysisExecutionService, asynchronously - Milestone 4 Architecture, ADR-013).
 */
public enum AnalysisRunStatus {
    RECEIVED,
    QUEUED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
