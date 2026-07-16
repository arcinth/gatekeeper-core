package com.gatekeeper.orchestration;

import com.gatekeeper.aireviewrun.AIReviewRunStatus;

/**
 * Published by AIReviewResultPersistenceService immediately after an
 * AIReviewRun reaches a terminal state (COMPLETED or FAILED) and is
 * persisted, in the same transaction (Unified Engineering Report
 * Architecture, Section 6). The other of the two independent triggers
 * ReportGenerationListener joins on - see VerdictProducedEvent's Javadoc for
 * the first.
 * <p>
 * Carries the id, for the same reason as AIReviewRequestedEvent. Also
 * carries {@code status} directly, unlike the id-only convention those
 * events follow: unlike the AnalysisRun entity that convention exists to
 * avoid passing, {@code status} is an immutable value already fully decided
 * at publish time, not a lazy-loaded JPA association that could be
 * stale/detached by the time a listener reads it - passing it avoids an
 * otherwise-pointless extra query with no staleness risk.
 */
record AIReviewFinishedEvent(Long analysisRunId, AIReviewRunStatus status) {
}
