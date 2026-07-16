package com.gatekeeper.report;

/**
 * Snapshots why an EngineeringReport's AI section looks the way it does at
 * publication time (Unified Engineering Report Architecture, Section 5).
 * This is the one piece of publication-time context worth persisting on the
 * report itself - everything else (Policy/Security/AI finding content,
 * Verdict outcome) is reconstructed at read time from its own system of
 * record, never duplicated here (ADR-044).
 * <p>
 * INCLUDED - a completed AIReviewRun existed by publication time; AI
 * findings are part of the report.
 * UNAVAILABLE - AI review was enabled but did not produce usable content in
 * time (failed, or the timeout sweep force-published before it finished).
 * The underlying reason stays on the AIReviewRun's own failureReason field,
 * not duplicated here (ADR-050).
 * DISABLED - AI review is turned off entirely for this deployment
 * ({@code gatekeeper.ai-review.enabled=false}); no AIReviewRun will ever
 * exist for this analysis run.
 */
public enum AiReviewStatus {
    INCLUDED,
    UNAVAILABLE,
    DISABLED
}
