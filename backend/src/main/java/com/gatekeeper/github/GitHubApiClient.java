package com.gatekeeper.github;

import com.gatekeeper.github.dto.InstallationAccessTokenResponse;
import com.gatekeeper.github.exception.GitHubApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The only class permitted to issue outbound HTTP calls to the GitHub REST API
 * (Architecture.md ADR-001 extends this isolation to the GitHub integration as
 * a whole; this client is the concrete enforcement point for that rule).
 */
@Component
public class GitHubApiClient {

    private final RestClient restClient;

    public GitHubApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${gatekeeper.github.api.base-url}") String baseUrl) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    }

    public InstallationAccessTokenResponse mintInstallationAccessToken(long installationId, String appJwt) {
        try {
            return restClient.post()
                    .uri("/app/installations/{installationId}/access_tokens", installationId)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + appJwt)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .body(InstallationAccessTokenResponse.class);
        } catch (RestClientResponseException ex) {
            throw new GitHubApiException(
                    "GitHub rejected the installation token request for installation " + installationId
                            + " (HTTP " + ex.getStatusCode().value() + ").", ex);
        } catch (RestClientException ex) {
            throw new GitHubApiException(
                    "Failed to reach GitHub while minting an installation token for installation "
                            + installationId + ".", ex);
        }
    }
}
