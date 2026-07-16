package com.gatekeeper.aireviewengine.provider;

import com.gatekeeper.aireviewengine.AIReviewContext;

/**
 * Turns an AIReviewContext into one provider's own request shape. Generic
 * over TRequest rather than tied to a single provider's DTO, so the contract
 * itself stays provider-agnostic even though each implementation (e.g.
 * AnthropicPromptBuilder) is not shared across providers - different
 * providers have genuinely different request shapes and prompting
 * conventions, so there is no useful cross-provider prompt logic to factor
 * out beyond this shared shape (Sprint 4 Architecture, Section 11).
 * <p>
 * A pure, side-effect-free transformation - no network I/O, no state -
 * independently unit-testable without a real provider call.
 */
public interface PromptBuilder<TRequest> {

    TRequest buildRequest(AIReviewContext context);
}
