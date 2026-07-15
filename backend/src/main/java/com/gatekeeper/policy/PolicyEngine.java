package com.gatekeeper.policy;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A reusable, deterministic rule engine: it knows how to run a collection of
 * PolicyRules and aggregate their output, and nothing about what any
 * individual rule checks for. Three properties make it "the Policy Engine
 * architecture" rather than just a loop:
 * <p>
 * <b>Discovery, not registration.</b> The constructor takes {@code List<PolicyRule>},
 * which Spring populates with every {@code @Component} bean implementing the
 * interface. Adding TodoCommentRule and FixmeCommentRule required zero
 * changes here - that's the Open/Closed guarantee this class exists to provide.
 * <p>
 * <b>Deterministic order.</b> Spring does not guarantee the injection order of
 * a {@code List<T>} of beans - it generally reflects classpath scan order,
 * which can change across JVMs, class loaders, or even unrelated code
 * changes. The same PolicyContext must always produce PolicyFindings in the
 * same order, so rules are re-sorted by id() once, in the constructor.
 * <p>
 * <b>Fault isolation.</b> One misbehaving rule (a bug, an unexpected null,
 * a regex catastrophe) must not prevent every other rule from running or
 * crash the whole AnalysisRun - each rule is evaluated inside its own
 * try/catch, mirroring the failure-isolation principle Architecture.md
 * applies at the engine level, just one level deeper.
 * <p>
 * Thread safety: {@link #rules} is built once, immutably, in the
 * constructor and never mutated afterward; {@link #evaluate} reads no
 * mutable shared state and writes none, so concurrent AnalysisRuns can call
 * it in parallel safely, provided every PolicyRule implementation upholds
 * the statelessness contract documented on {@link PolicyRule}.
 */
@Slf4j
@Component
public class PolicyEngine {

    private final List<PolicyRule> rules;

    public PolicyEngine(List<PolicyRule> rules) {
        this.rules = rules.stream()
                .sorted(Comparator.comparing(PolicyRule::id))
                .toList();
        log.info("PolicyEngine initialized with {} rule(s): {}", this.rules.size(), ruleIds());
    }

    public PolicyResult evaluate(PolicyContext context) {
        List<PolicyFinding> findings = rules.stream()
                .flatMap(rule -> evaluateSafely(rule, context).stream())
                .toList();

        return new PolicyResult(context.analysisRunId(), findings, rules.size(), Instant.now());
    }

    /**
     * Runs one rule and converts any failure into "this rule found nothing"
     * rather than letting it propagate - a broken rule is a bug to fix in
     * that rule, not a reason to stop analyzing the other N-1 rules.
     */
    private List<PolicyFinding> evaluateSafely(PolicyRule rule, PolicyContext context) {
        try {
            List<PolicyFinding> findings = Optional.ofNullable(rule.evaluate(context)).orElse(List.of());
            log.debug("Rule '{}' produced {} finding(s) for analysis run {}.",
                    rule.id(), findings.size(), context.analysisRunId());
            return findings;
        } catch (RuntimeException ex) {
            log.error("Policy rule '{}' threw during evaluation of analysis run {}; excluding it from this result.",
                    rule.id(), context.analysisRunId(), ex);
            return List.of();
        }
    }

    private List<String> ruleIds() {
        return rules.stream().map(PolicyRule::id).toList();
    }
}
