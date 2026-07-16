package com.gatekeeper.verdictengine;

/**
 * The governance decision produced for one AnalysisRun. Deliberately binary,
 * unlike AnalysisRunStatus's five states - nothing in this sprint's scope has
 * a consumer for a third state (e.g. "needs manual review"), and this follows
 * the same minimum-states-actually-needed discipline AIReviewRunStatus
 * already established (Sprint 5 Architecture, ADR-042).
 */
public enum VerdictOutcome {
    APPROVED,
    BLOCKED
}
