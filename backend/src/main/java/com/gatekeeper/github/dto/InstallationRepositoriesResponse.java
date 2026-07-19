package com.gatekeeper.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Response from GET /installation/repositories - GitHub's own mechanism for
 * "which repositories can this installation see right now." Needed because
 * the installation webhook family does not reliably deliver the complete
 * repository list at install time: installation_repositories covers changes
 * to an existing installation's selection, not the initial one, and
 * installation's own payload cannot be depended on to enumerate every
 * repository (particularly under repository_selection "all"). This endpoint
 * is the authoritative source instead.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstallationRepositoriesResponse(
        @JsonProperty("total_count") int totalCount,
        List<RepositorySummary> repositories) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RepositorySummary(Long id, String name, @JsonProperty("full_name") String fullName) {
    }
}
