package com.gatekeeper.verdictengine;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A reusable, deterministic decision engine: it knows how to run a
 * collection of VerdictRules and reduce their output into a single
 * governance decision, and nothing about what any individual rule checks
 * for. Structurally mirrors com.gatekeeper.policy.PolicyEngine and
 * com.gatekeeper.securityengine.SecurityEngine (Sprint 5 Architecture,
 * Section 6 / ADR-036) - not com.gatekeeper.aireviewengine.AIReviewEngine's
 * single-provider shape, since a verdict decision naturally decomposes into
 * independent, named, individually-explainable checks the same way Policy's
 * and Security's checks do, unlike a single LLM call.
 * <p>
 * <b>Discovery, not registration.</b> The constructor takes
 * {@code List<VerdictRule>}, which Spring populates with every
 * {@code @Component} bean implementing the interface. Adding a new rule
 * (including a future organization-specific threshold rule) requires zero
 * changes here.
 * <p>
 * <b>Deterministic order.</b> Spring does not guarantee the injection order
 * of a {@code List<T>} of beans, so rules are re-sorted by id() once, in the
 * constructor - the same VerdictContext must always produce the same
 * VerdictResult in the same reason order.
 * <p>
 * <b>Fault isolation.</b> One misbehaving rule must not prevent every other
 * rule from voting - each rule is evaluated inside its own try/catch,
 * exactly like PolicyEngine/SecurityEngine. This is deliberately narrower
 * than the guarantee the caller of {@link #evaluate} gets as a whole: a
 * failure inside this engine's own reduction logic (not inside any one
 * rule) is not caught here and propagates to the caller (Sprint 5
 * Architecture, Section 16 / ADR-038) - unlike AI Review, a Verdict Engine
 * failure is not something the platform silently degrades around, since a
 * completed AnalysisRun with no verdict would defeat the platform's entire
 * purpose.
 * <p>
 * <b>Outcome derivation.</b> BLOCKED if any collected VerdictReason has
 * {@code blocking() == true}; APPROVED otherwise, including when zero rules
 * are registered or zero reasons are produced. This reduction step lives
 * here, not in any individual rule - no single rule "owns" the final
 * outcome, which is what makes adding/removing rules safe and independent.
 * <p>
 * Thread safety: {@link #rules} is built once, immutably, in the
 * constructor and never mutated afterward; {@link #evaluate} reads no
 * mutable shared state and writes none, so concurrent AnalysisRuns can call
 * it in parallel safely, provided every VerdictRule implementation upholds
 * the statelessness contract documented on {@link VerdictRule}.
 * <p>
 * No I/O, ever: no database query, no HTTP call, no file access - this is
 * what makes the synchronous integration point described in the Sprint 5
 * Architecture (Section 10/17) safe to run inside an existing transaction.
 */
@Slf4j
@Component
public class VerdictEngine {

    private final List<VerdictRule> rules;

    public VerdictEngine(List<VerdictRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparing(VerdictRule::id))
                .toList();
        log.info("VerdictEngine initialized with {} rule(s): {}", this.rules.size(), ruleIds());
    }

    public VerdictResult evaluate(VerdictContext context) {
        List<VerdictReason> reasons = rules.stream()
                .flatMap(rule -> evaluateSafely(rule, context).stream())
                .toList();

        VerdictOutcome outcome = reasons.stream().anyMatch(VerdictReason::blocking)
                ? VerdictOutcome.BLOCKED
                : VerdictOutcome.APPROVED;

        return new VerdictResult(context.analysisRunId(), outcome, reasons, Instant.now());
    }

    /**
     * Runs one rule and converts any failure into "this rule has nothing to
     * report" rather than letting it propagate - a broken rule is a bug to
     * fix in that rule, not a reason to stop evaluating the other N-1 rules
     * or to fail the whole verdict.
     */
    private List<VerdictReason> evaluateSafely(VerdictRule rule, VerdictContext context) {
        try {
            List<VerdictReason> reasons = Optional.ofNullable(rule.evaluate(context)).orElse(List.of());
            log.debug("Rule '{}' produced {} reason(s) for analysis run {}.",
                    rule.id(), reasons.size(), context.analysisRunId());
            return reasons;
        } catch (RuntimeException ex) {
            log.error("Verdict rule '{}' threw during evaluation of analysis run {}; excluding it from this result.",
                    rule.id(), context.analysisRunId(), ex);
            return List.of();
        }
    }

    private List<String> ruleIds() {
        return rules.stream().map(VerdictRule::id).toList();
    }
}
