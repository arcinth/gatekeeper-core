package com.gatekeeper.aireviewengine;

/**
 * How confident the AI provider was in one finding. Deliberately not named or
 * shaped like PolicySeverity/SecuritySeverity, and deliberately not reusing
 * either enum: a deterministic rule can claim a finding IS critical, with
 * earned authority. An LLM cannot make that same claim about its own output -
 * it can only indicate how confident it is in an observation, which is a
 * weaker, different kind of statement. Keeping the name and the enum distinct
 * enforces "advisory only" at the type-system level everywhere this data
 * travels (API, persistence, dashboard), not just in documentation
 * (Sprint 4 Architecture, Section 8 / ADR-032).
 */
public enum AIReviewConfidence {
    LOW,
    MEDIUM,
    HIGH
}
