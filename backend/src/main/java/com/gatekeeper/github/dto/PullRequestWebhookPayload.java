package com.gatekeeper.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * The subset of GitHub's "pull_request" webhook payload GateKeeper actually
 * uses. GitHub sends many more fields (labels, requested_reviewers, milestone,
 * links, ...); they're irrelevant to ingestion and intentionally left unmodeled
 * rather than mapped, hence @JsonIgnoreProperties at every level.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PullRequestWebhookPayload(
        String action,
        @JsonProperty("pull_request") PullRequestData pullRequest,
        RepositoryData repository,
        InstallationData installation) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PullRequestData(
            Long id,
            Integer number,
            String title,
            UserData user,
            BranchRef head,
            BranchRef base,
            String state,
            boolean merged) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserData(String login) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BranchRef(String ref, String sha) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositoryData(
            Long id,
            @JsonProperty("full_name") String fullName,
            @JsonProperty("default_branch") String defaultBranch) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstallationData(Long id) {
    }
}
