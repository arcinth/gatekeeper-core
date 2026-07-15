package com.gatekeeper.policyfinding.dto;

import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import java.time.Instant;

/** All fields optional - null means "not filtered on this criterion" (Milestone 5 Architecture, Section 5). */
public record PolicyFindingFilter(
        Long analysisRunId,
        Long repositoryId,
        PolicySeverity severity,
        PolicyCategory category,
        String ruleId,
        Instant from,
        Instant to) {
}
