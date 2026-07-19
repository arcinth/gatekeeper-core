package com.gatekeeper.policyconfiguration.dto;

import com.gatekeeper.policy.PolicySeverity;
import jakarta.validation.constraints.NotNull;

/** severityOverride is deliberately nullable/omittable - null means "no override, use the rule's own default". */
public record UpdatePolicyConfigurationRequest(@NotNull Boolean enabled, PolicySeverity severityOverride) {
}
