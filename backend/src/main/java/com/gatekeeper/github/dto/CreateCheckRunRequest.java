package com.gatekeeper.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Request body for POST /repos/{owner}/{repo}/check-runs. GateKeeper always
 * creates a check run already resolved (status "completed" with a
 * conclusion) rather than in two phases ("in_progress" then "completed"),
 * since by the time GitHubCheckRunService runs (after VerdictProducedEvent)
 * the analysis is already finished - there is no earlier point in this
 * sprint's scope from which to publish a "queued"/"in_progress" state.
 */
public record CreateCheckRunRequest(
        String name,
        @JsonProperty("head_sha") String headSha,
        String status,
        String conclusion,
        @JsonProperty("started_at") Instant startedAt,
        @JsonProperty("completed_at") Instant completedAt,
        CheckRunOutput output) {
}
