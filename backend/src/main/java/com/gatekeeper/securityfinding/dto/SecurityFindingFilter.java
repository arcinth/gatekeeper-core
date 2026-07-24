package com.gatekeeper.securityfinding.dto;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.time.Instant;

/**
 * All fields optional except currentOnly - null means "not filtered on this
 * criterion" (mirrors PolicyFindingFilter). currentOnly is a plain boolean,
 * not tri-state: it defaults to true at the controller so the triage queue
 * never silently shows findings from a pull request's superseded analysis
 * runs, or from a pull request that has since merged or closed.
 */
public record SecurityFindingFilter(
        Long analysisRunId,
        Long repositoryId,
        SecuritySeverity severity,
        SecurityCategory category,
        String ruleId,
        Instant from,
        Instant to,
        boolean currentOnly) {
}
