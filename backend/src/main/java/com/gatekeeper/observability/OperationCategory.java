package com.gatekeeper.observability;

/**
 * The fixed set of operation categories {@link ObservedOperation} can tag
 * (Milestone 9: Observability) - deliberately closed and small, matching the
 * exact list of "monitor these" boundaries named in the milestone: GitHub
 * API calls, and the three analysis engines plus the pipeline that runs
 * them. Each has its own configurable slow-operation threshold (see
 * {@code gatekeeper.observability.thresholds.*} in application.yml and
 * {@link ObservedOperationAspect}) - adding a 6th category is a deliberate,
 * two-place change (a new constant here, a new threshold property), the same
 * "closed set that must be edited, not silently extended" shape already used
 * elsewhere in this codebase (e.g. {@code ReviewDecisionConclusionMapper}'s
 * exhaustive switch).
 */
public enum OperationCategory {
    GITHUB_API,
    POLICY_ENGINE,
    SECURITY_ENGINE,
    REVIEW_ENGINE,
    ANALYSIS_PIPELINE
}
