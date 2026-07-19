package com.gatekeeper.reviewdecision;

/**
 * Deliberately two values, not GitHub's multi-state review model (APPROVE /
 * REQUEST_CHANGES / COMMENT) - mirrors the product vision's literal wording
 * ("Reviewer... Approves or rejects"), not GitHub's review semantics.
 */
public enum ReviewDecisionType {
    APPROVED,
    REJECTED
}
