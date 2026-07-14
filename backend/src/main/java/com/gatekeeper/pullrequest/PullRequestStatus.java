package com.gatekeeper.pullrequest;

/**
 * Mirrors the lifecycle states a GitHub Pull Request can be in, per
 * docs/Domain-Model.md. Derived from a webhook's `state`/`merged` fields by
 * PullRequestService - never set directly by callers - so it can't drift out
 * of sync with what GitHub actually reports.
 */
public enum PullRequestStatus {
    OPEN,
    CLOSED,
    MERGED
}
