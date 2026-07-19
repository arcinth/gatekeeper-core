package com.gatekeeper.policy;

import java.util.Map;
import java.util.Optional;

/**
 * An immutable snapshot of one organization's policy overrides, as of the
 * moment it was built (Milestone 6: Policy Management). {@link PolicyEngine}
 * receives this as a plain value - it never queries a database itself, only
 * asks this snapshot two questions per rule. Both maps are defensively
 * copied into unmodifiable form at construction, so a caller holding a
 * reference to the original mutable map cannot change this snapshot's
 * answers after the fact - once built, an evaluation using this snapshot
 * will see the exact same configuration no matter how long it takes to run.
 * <p>
 * A rule id absent from both maps means "use that rule's own default" -
 * {@link #EMPTY} (every rule enabled, no severity overrides) is exactly
 * today's pre-Milestone-6 behavior, and is what an organization with no
 * {@code policy_configurations} rows effectively gets.
 */
public final class PolicyConfigurationSet {

    public static final PolicyConfigurationSet EMPTY = new PolicyConfigurationSet(Map.of(), Map.of());

    private final Map<String, Boolean> enabledByRuleId;
    private final Map<String, PolicySeverity> severityOverrideByRuleId;

    public PolicyConfigurationSet(
            Map<String, Boolean> enabledByRuleId, Map<String, PolicySeverity> severityOverrideByRuleId) {
        this.enabledByRuleId = Map.copyOf(enabledByRuleId);
        this.severityOverrideByRuleId = Map.copyOf(severityOverrideByRuleId);
    }

    /** Defaults to enabled - a rule id this snapshot has no opinion on has never been disabled. */
    public boolean isEnabled(String ruleId) {
        return enabledByRuleId.getOrDefault(ruleId, true);
    }

    public Optional<PolicySeverity> severityOverride(String ruleId) {
        return Optional.ofNullable(severityOverrideByRuleId.get(ruleId));
    }
}
