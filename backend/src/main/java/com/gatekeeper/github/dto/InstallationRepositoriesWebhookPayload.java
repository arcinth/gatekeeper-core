package com.gatekeeper.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * The subset of GitHub's "installation_repositories" webhook payload
 * GateKeeper uses. GitHub sends one populated array and one empty array
 * depending on action ("added" or "removed"), but both are modeled and read
 * independently here rather than branching on action, since nothing about
 * processing repositories_added or repositories_removed actually depends on
 * knowing which action name produced them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstallationRepositoriesWebhookPayload(
        String action,
        InstallationReference installation,
        @JsonProperty("repositories_added") List<RepositoryReference> repositoriesAdded,
        @JsonProperty("repositories_removed") List<RepositoryReference> repositoriesRemoved) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InstallationReference(Long id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositoryReference(Long id, String name, @JsonProperty("full_name") String fullName) {
    }
}
