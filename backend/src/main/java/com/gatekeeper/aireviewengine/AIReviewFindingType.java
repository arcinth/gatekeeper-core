package com.gatekeeper.aireviewengine;

/**
 * What kind of observation an AI Review finding represents. A fixed,
 * platform-defined vocabulary, not free text - the provider's raw output is
 * normalized into this enum at the provider-implementation boundary (Sprint 4
 * Architecture, Section 12), so nothing downstream ever has to parse or trust
 * an arbitrary AI-authored category string.
 */
public enum AIReviewFindingType {
    SUGGESTION,
    POTENTIAL_BUG,
    STYLE,
    PERFORMANCE,
    CLARITY
}
