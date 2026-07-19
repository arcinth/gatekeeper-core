package com.gatekeeper.policyconfiguration.dto;

import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicyRule;
import com.gatekeeper.policy.PolicySeverity;

/**
 * One PolicyRule's catalog entry merged with an organization's effective
 * configuration for it. {@code ruleId}/{@code description}/{@code defaultCategory}/
 * {@code defaultSeverity} always come from the live {@link PolicyRule} bean,
 * never from the database - see docs/Policy-Development.md for why the
 * catalog is never duplicated in policy_configurations.
 */
public record PolicyConfigurationResponse(
        String ruleId,
        String description,
        PolicyCategory defaultCategory,
        PolicySeverity defaultSeverity,
        boolean enabled,
        PolicySeverity severity,
        boolean overridden) {

    /** No organization override exists yet: every value is the rule's own default. */
    public static PolicyConfigurationResponse defaultFor(PolicyRule rule) {
        return new PolicyConfigurationResponse(
                rule.id(), rule.description(), rule.defaultCategory(), rule.defaultSeverity(),
                true, rule.defaultSeverity(), false);
    }

    public static PolicyConfigurationResponse from(PolicyRule rule, boolean enabled, PolicySeverity severityOverride) {
        PolicySeverity effectiveSeverity = severityOverride != null ? severityOverride : rule.defaultSeverity();
        boolean overridden = !enabled || severityOverride != null;
        return new PolicyConfigurationResponse(
                rule.id(), rule.description(), rule.defaultCategory(), rule.defaultSeverity(),
                enabled, effectiveSeverity, overridden);
    }
}
