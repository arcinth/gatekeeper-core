package com.gatekeeper.aireviewengine;

/**
 * The extension point future AI providers plug into. AIReviewEngine depends
 * on exactly one AIReviewProvider, not a list - "extensible to multiple
 * providers" (Sprint 4 Architecture, Section 10 / ADR-034) means a second
 * implementation is easy to add and select via configuration, not that
 * GateKeeper calls multiple providers per run or races them (explicitly a
 * non-goal).
 * <p>
 * Implementations must be stateless and thread-safe: a single AIReviewProvider
 * bean is shared across every concurrent AIReviewEngine#evaluate() call
 * (Spring beans are singletons by default) - the identical contract
 * PolicyRule/SecurityRule already document.
 */
public interface AIReviewProvider {

    /**
     * A short, stable identifier for this provider (e.g. "anthropic-claude"),
     * recorded on AIReviewResult#provider() for traceability once multiple
     * providers exist.
     */
    String providerName();

    /**
     * The model this provider is currently configured to call (e.g.
     * "claude-opus-4-6"). Static, configuration-derived, and always available
     * regardless of whether any particular {@link #review} call succeeds -
     * added in Sprint 4 Milestone 3 so orchestration can persist this
     * metadata on every AI review run, including failed ones, without
     * depending on a successful {@link AIReviewResult}.
     */
    String modelName();

    /**
     * The prompt version this provider is currently configured to use (e.g.
     * "v1"). Same availability guarantee as {@link #modelName()}.
     */
    String promptVersion();

    /**
     * Reviews the given context and returns a result. Must throw
     * {@link com.gatekeeper.aireviewengine.exception.AIProviderException}
     * (or a subtype) on any failure - a network error, a timeout, an HTTP
     * error response, or a response that could not be parsed or trusted.
     * Never returns a result representing failure; failure is always a
     * thrown exception, exactly as AIReviewResult's own Javadoc documents.
     */
    AIReviewResult review(AIReviewContext context);
}
