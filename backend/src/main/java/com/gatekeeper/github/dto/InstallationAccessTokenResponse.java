package com.gatekeeper.github.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Response body from GitHub's POST /app/installations/{id}/access_tokens.
 * GitHub returns additional fields (permissions, repositories, ...) that are
 * irrelevant to Milestone 1 and intentionally ignored rather than modeled.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InstallationAccessTokenResponse(
        String token,
        @JsonProperty("expires_at") Instant expiresAt) {
}
