package com.gatekeeper.github.exception;

/**
 * Signals that a call to the GitHub REST API failed. Deliberately not an
 * ApiException: how this should surface to a client (if at all) depends on the
 * calling context - a webhook handler and a future "fetch PR diff" endpoint
 * would translate this differently, so translation is left to the caller.
 */
public class GitHubApiException extends RuntimeException {

    public GitHubApiException(String message) {
        super(message);
    }

    public GitHubApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
