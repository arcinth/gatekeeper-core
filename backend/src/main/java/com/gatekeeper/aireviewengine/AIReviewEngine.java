package com.gatekeeper.aireviewengine;

import org.springframework.stereotype.Component;

/**
 * Orchestrates a single AIReviewProvider. Deliberately NOT a mirror of
 * PolicyEngine/SecurityEngine's internal mechanism - those aggregate many
 * small, independent, auto-discovered rules ({@code List<PolicyRule>})
 * because a deterministic check naturally decomposes into many tiny regexes.
 * An LLM call has no equivalent decomposition: GateKeeper sends one request
 * and gets back one structured response. There is no {@code List<AIReviewRule>}
 * here, and building one would manufacture structure that doesn't exist in
 * the domain (Sprint 4 Architecture, Section 6).
 * <p>
 * Holds a single immutable provider reference and no other state, so
 * concurrent callers with distinct contexts are safe by construction -
 * nothing here is shared or mutated between calls.
 * <p>
 * Any exception thrown by the provider propagates uncaught: there is no
 * "other rule" to isolate a failure from the way PolicyEngine/SecurityEngine
 * isolate one rule's failure from the rest, and AIReviewResult is deliberately
 * success-shaped only (see its own Javadoc) - the future caller (not built in
 * this milestone) is responsible for catching and recording a degraded
 * outcome, exactly as AnalysisExecutionService already does for the
 * deterministic engines.
 * <p>
 * A Spring-managed bean since Sprint 4 Milestone 2: AnthropicAIReviewProvider
 * is now the sole concrete AIReviewProvider bean in the application context,
 * so Spring can autowire this constructor unambiguously by type. Supporting a
 * second provider in the future (e.g. a different model or vendor) will need
 * an explicit selection mechanism - a qualifier, a per-provider profile, or a
 * routing provider - deliberately not built now, since there is nothing yet
 * to select between.
 */
@Component
public class AIReviewEngine {

    private final AIReviewProvider provider;

    public AIReviewEngine(AIReviewProvider provider) {
        this.provider = provider;
    }

    public AIReviewResult evaluate(AIReviewContext context) {
        return provider.review(context);
    }
}
