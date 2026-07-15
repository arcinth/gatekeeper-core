package com.gatekeeper.github;

import com.gatekeeper.github.dto.GitHubFileChange;
import com.gatekeeper.github.dto.InstallationAccessTokenResponse;
import com.gatekeeper.github.exception.GitHubApiException;
import com.gatekeeper.github.exception.GitHubTransientApiException;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * The only class permitted to issue outbound HTTP calls to the GitHub REST API
 * (Architecture.md ADR-001 extends this isolation to the GitHub integration as
 * a whole; this client is the concrete enforcement point for that rule).
 */
@Slf4j
@Component
public class GitHubApiClient {

    private static final int FILES_PER_PAGE = 100;

    private final RestClient restClient;
    private final int maxChangedFilesPerPullRequest;

    public GitHubApiClient(
            RestClient.Builder restClientBuilder,
            @Value("${gatekeeper.github.api.base-url}") String baseUrl,
            @Value("${gatekeeper.analysis.max-changed-files-per-pull-request}") int maxChangedFilesPerPullRequest) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.maxChangedFilesPerPullRequest = maxChangedFilesPerPullRequest;
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

    /**
     * Fetches the files changed in a Pull Request, paginated. Retried only for
     * transient failures (GitHubTransientApiException) - a permanent failure
     * (bad auth, PR not found) fails immediately rather than wasting three
     * attempts on something a retry can't fix (Milestone 4 Architecture, ADR-017).
     * <p>
     * repositoryFullName is split into owner/repo and passed as two separate
     * URI template variables rather than interpolated as one - a single
     * "{repo}" variable containing "org/repo" would have its internal slash
     * percent-encoded by URI template expansion, silently producing a URL
     * GitHub would 404 on.
     */
    @Retryable(
            retryFor = GitHubTransientApiException.class,
            maxAttemptsExpression = "${gatekeeper.github.api.retry.max-attempts:3}",
            backoff = @Backoff(
                    delayExpression = "${gatekeeper.github.api.retry.initial-backoff-ms:500}",
                    multiplierExpression = "${gatekeeper.github.api.retry.backoff-multiplier:2}"))
    public List<GitHubFileChange> fetchPullRequestFiles(
            String repositoryFullName, int pullRequestNumber, String installationAccessToken) {
        String[] ownerAndRepo = repositoryFullName.split("/", 2);
        if (ownerAndRepo.length != 2) {
            throw new IllegalArgumentException(
                    "repositoryFullName must be in 'owner/repo' form, got: " + repositoryFullName);
        }
        String owner = ownerAndRepo[0];
        String repo = ownerAndRepo[1];

        List<GitHubFileChange> allFiles = new ArrayList<>();
        int page = 1;
        while (allFiles.size() < maxChangedFilesPerPullRequest) {
            List<GitHubFileChange> pageOfFiles =
                    fetchFilesPage(owner, repo, pullRequestNumber, installationAccessToken, page);
            allFiles.addAll(pageOfFiles);
            if (pageOfFiles.size() < FILES_PER_PAGE) {
                break;
            }
            page++;
        }

        if (allFiles.size() > maxChangedFilesPerPullRequest) {
            log.warn("PR {}/{}#{} has more than {} changed files; truncating to the configured limit.",
                    owner, repo, pullRequestNumber, maxChangedFilesPerPullRequest);
            return allFiles.subList(0, maxChangedFilesPerPullRequest);
        }
        return allFiles;
    }

    private List<GitHubFileChange> fetchFilesPage(
            String owner, String repo, int pullRequestNumber, String installationAccessToken, int page) {
        try {
            GitHubFileChange[] response = restClient.get()
                    .uri("/repos/{owner}/{repo}/pulls/{number}/files?per_page={perPage}&page={page}",
                            owner, repo, pullRequestNumber, FILES_PER_PAGE, page)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + installationAccessToken)
                    .header(HttpHeaders.ACCEPT, "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .retrieve()
                    .body(GitHubFileChange[].class);
            return response == null ? List.of() : List.of(response);
        } catch (RestClientResponseException ex) {
            String message = "GitHub returned an error fetching changed files for " + owner + "/" + repo
                    + "#" + pullRequestNumber + " (HTTP " + ex.getStatusCode().value() + ").";
            if (isTransient(ex.getStatusCode())) {
                throw new GitHubTransientApiException(message, ex);
            }
            throw new GitHubApiException(message, ex);
        } catch (RestClientException ex) {
            throw new GitHubTransientApiException(
                    "Failed to reach GitHub while fetching changed files for " + owner + "/" + repo
                            + "#" + pullRequestNumber + ".", ex);
        }
    }

    /**
     * 403 is deliberately treated as transient here even though GitHub also
     * uses it for genuine permission errors, not just rate limiting:
     * distinguishing the two precisely requires inspecting rate-limit response
     * headers, which this client doesn't parse yet. Retrying a real permission
     * error three times costs a few seconds before it fails anyway; not
     * retrying a real rate limit guarantees failure when a short backoff might
     * have recovered - the asymmetry favors treating 403 as retryable.
     */
    private boolean isTransient(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == 429 || status.value() == 403;
    }
}
