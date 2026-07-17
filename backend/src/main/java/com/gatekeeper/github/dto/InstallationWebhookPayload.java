package com.gatekeeper.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * The subset of GitHub's "installation" webhook payload GateKeeper uses for
 * onboarding. GitHub sends this same shape for every action (created,
 * deleted, new_permissions_accepted, suspend, unsuspend, ...); the
 * "repositories" array some actions include is part of the
 * installation_repositories family and is intentionally left unmodeled here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstallationWebhookPayload(String action, InstallationData installation) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstallationData(
            Long id,
            AccountData account,
            @JsonProperty("repository_selection") String repositorySelection,
            Map<String, String> permissions) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountData(Long id, String login, String type) {
    }
}
