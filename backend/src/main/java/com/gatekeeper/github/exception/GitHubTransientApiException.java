package com.gatekeeper.github.exception;

/**
 * A GitHub API failure worth retrying: a 5xx response, a rate limit, or a
 * network-level failure with no response at all. Kept distinct from the base
 * GitHubApiException (used for permanent failures like 401/404) so
 * GitHubApiClient's retry annotation can target this subtype specifically -
 * retrying a permanent failure three times would only waste time before
 * failing anyway (Milestone 4 Architecture, ADR-017).
 */
public class GitHubTransientApiException extends GitHubApiException {

    public GitHubTransientApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
