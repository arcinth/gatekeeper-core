package com.gatekeeper.github.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Request body for PATCH /repos/{owner}/{repo}/check-runs/{check_run_id}.
 * No head_sha (the check run id in the URL already identifies which run is
 * being updated) and no "name" - GitHub does not require re-sending it, and
 * GateKeeper never renames a check run once created.
 */
public record UpdateCheckRunRequest(
        String status,
        String conclusion,
        @JsonProperty("completed_at") Instant completedAt,
        CheckRunOutput output) {
}
