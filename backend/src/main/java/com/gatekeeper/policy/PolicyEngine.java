package com.gatekeeper.policy;

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
 * same order, so rules are re-sorted by id() once, in the constructor. That
 * same constructor also fails fast if two discovered rules share an id
 * (Milestone 6: Policy Management) - rule ids are the key an organization's
 * override is stored against, so a collision would make one rule silently
 * inherit another's configuration (see PolicyRule#id's own Javadoc).
 * <p>
 * <b>Fault isolation.</b> One misbehaving rule (a bug, an unexpected null,
 * a regex catastrophe) must not prevent every other rule from running or
 * crash the whole AnalysisRun - each rule is evaluated inside its own
 * try/catch, mirroring the failure-isolation principle Architecture.md
 * applies at the engine level, just one level deeper.
 * <p>
 * <b>Configuration is a parameter, not a lookup (Milestone 6).</b>
 * {@link #evaluate} takes a {@link PolicyConfigurationSet} snapshot rather
 * than querying anything itself - this class remains a pure, persistence-free
 * computation exactly as it always has been; PolicyEngineService (the
 * already-designated integration boundary, see its own Javadoc) is the only
 * caller that knows a database exists.
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
        requireUniqueIds(this.rules);
        log.info("PolicyEngine initialized with {} rule(s): {}", this.rules.size(), ruleIds());
    }

    /**
     * Runs every rule the organization has not disabled, then remaps each
     * produced finding's severity when that organization has overridden it.
     * {@code configuration} is an immutable snapshot (see its own Javadoc) -
     * every finding in the returned result reflects exactly the configuration
     * passed in, never a value read at some other point during evaluation.
     */
    @ObservedOperation(value = "policy.evaluate", category = OperationCategory.POLICY_ENGINE)
    public PolicyResult evaluate(PolicyContext context, PolicyConfigurationSet configuration) {
        List<PolicyRule> enabledRules = rules.stream()
                .filter(rule -> configuration.isEnabled(rule.id()))
                .toList();

        List<PolicyFinding> findings = enabledRules.stream()
                .flatMap(rule -> evaluateSafely(rule, context).stream())
                .map(finding -> applyOverride(finding, configuration))
                .toList();

        return new PolicyResult(context.analysisRunId(), findings, enabledRules.size(), Instant.now());
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

    private PolicyFinding applyOverride(PolicyFinding finding, PolicyConfigurationSet configuration) {
        return configuration.severityOverride(finding.ruleId())
                .<PolicyFinding>map(severity -> new PolicyFinding(
                        finding.ruleId(),
                        finding.category(),
                        severity,
                        finding.filePath(),
                        finding.lineNumber(),
                        finding.message(),
                        finding.recommendation()))
                .orElse(finding);
    }

    private void requireUniqueIds(List<PolicyRule> sortedRules) {
        for (int i = 1; i < sortedRules.size(); i++) {
            if (sortedRules.get(i - 1).id().equals(sortedRules.get(i).id())) {
                throw new IllegalStateException(
                        "Two PolicyRule beans share the id '" + sortedRules.get(i).id() + "' - rule ids must be "
                                + "unique (see PolicyRule#id's Javadoc); this is a startup configuration error, not "
                                + "something PolicyEngine can resolve on its own.");
            }
        }
    }

    private List<String> ruleIds() {
        return rules.stream().map(PolicyRule::id).toList();
    }
}
