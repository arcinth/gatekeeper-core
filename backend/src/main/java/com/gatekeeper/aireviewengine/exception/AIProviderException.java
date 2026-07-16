package com.gatekeeper.aireviewengine.exception;

/**
 * Signals that a call to an AI provider failed, or that its response could
 * not be trusted (unparseable, empty, malformed). Deliberately not an
 * ApiException: how this should surface (if at all) depends entirely on the
 * calling context - the future orchestration layer translates this into a
 * per-run UNAVAILABLE/FAILED outcome, never into an AnalysisRun failure
 * (Sprint 4 Architecture, Section 13 / ADR-031). Mirrors GitHubApiException's
 * shape exactly.
 */
public class AIProviderException extends RuntimeException {

    public AIProviderException(String message) {
        super(message);
    }

    public AIProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
