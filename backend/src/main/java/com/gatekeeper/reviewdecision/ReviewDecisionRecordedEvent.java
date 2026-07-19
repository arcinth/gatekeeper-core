package com.gatekeeper.reviewdecision;

/**
 * Published by ReviewDecisionService immediately after a ReviewDecision is
 * persisted (Milestone 4). Triggers GitHubReviewDecisionCheckRunPublisher (in
 * the orchestration package) to publish the decision onto GitHub's separate
 * "GateKeeper Review" check run - this event is public, unlike
 * VerdictProducedEvent/InstallationRepositorySyncRequestedEvent, because its
 * publisher and listener live in different packages.
 * <p>
 * Carries only the id, the same convention every other event in this
 * codebase already established: the AnalysisRun/ReviewDecision were loaded in
 * the publisher's own transaction and would be stale/unsafe to touch from the
 * listener's own transaction and thread.
 */
public record ReviewDecisionRecordedEvent(Long analysisRunId) {
}
