package com.gatekeeper.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Response from POST /repos/{owner}/{repo}/check-runs. GitHub returns many
 * more fields (html_url, status, output, ...); only the id is needed, to
 * remember which check run a later update should target.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckRunResponse(Long id) {
}
