package com.gatekeeper.securityengine;

import com.gatekeeper.observability.ObservedOperation;
import com.gatekeeper.observability.OperationCategory;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A reusable, deterministic rule engine: it knows how to run a collection of
 * SecurityRules and aggregate their output, and nothing about what any
 * individual rule checks for. Structurally identical to
 * com.gatekeeper.policy.PolicyEngine (Sprint 3 Security Engine Architecture,
 * Section 5) - same three properties:
 * <p>
 * <b>Discovery, not registration.</b> The constructor takes {@code List<SecurityRule>},
 * which Spring populates with every {@code @Component} bean implementing the
 * interface. Adding a new rule requires zero changes here.
 * <p>
 * <b>Deterministic order.</b> Spring does not guarantee the injection order of
 * a {@code List<T>} of beans, so rules are re-sorted by id() once, in the
 * constructor - the same SecurityContext must always produce SecurityFindings
 * in the same order.
 * <p>
 * <b>Fault isolation.</b> One misbehaving rule must not prevent every other
 * rule from running or crash the whole AnalysisRun - each rule is evaluated
 * inside its own try/catch.
 * <p>
 * Thread safety: {@link #rules} is built once, immutably, in the constructor
 * and never mutated afterward; {@link #evaluate} reads no mutable shared
 * state and writes none, so concurrent AnalysisRuns can call it in parallel
 * safely, provided every SecurityRule implementation upholds the
 * statelessness contract documented on {@link SecurityRule}.
 */
@Slf4j
@Component
public class SecurityEngine {

    private final List<SecurityRule> rules;

    public SecurityEngine(List<SecurityRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparing(SecurityRule::id))
                .toList();
        log.info("SecurityEngine initialized with {} rule(s): {}", this.rules.size(), ruleIds());
    }

    @ObservedOperation(value = "security.evaluate", category = OperationCategory.SECURITY_ENGINE)
    public SecurityResult evaluate(SecurityContext context) {
        List<SecurityFinding> findings = rules.stream()
                .flatMap(rule -> evaluateSafely(rule, context).stream())
                .toList();

        return new SecurityResult(context.analysisRunId(), findings, rules.size(), Instant.now());
    }

    /**
     * Runs one rule and converts any failure into "this rule found nothing"
     * rather than letting it propagate - a broken rule is a bug to fix in
     * that rule, not a reason to stop evaluating the other N-1 rules.
     */
    private List<SecurityFinding> evaluateSafely(SecurityRule rule, SecurityContext context) {
        try {
            List<SecurityFinding> findings = Optional.ofNullable(rule.evaluate(context)).orElse(List.of());
            log.debug("Rule '{}' produced {} finding(s) for analysis run {}.",
                    rule.id(), findings.size(), context.analysisRunId());
            return findings;
        } catch (RuntimeException ex) {
            log.error("Security rule '{}' threw during evaluation of analysis run {}; excluding it from this result.",
                    rule.id(), context.analysisRunId(), ex);
            return List.of();
        }
    }

    private List<String> ruleIds() {
        return rules.stream().map(SecurityRule::id).toList();
    }
}
