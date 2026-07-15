package com.gatekeeper.securityfinding.dto;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.time.Instant;

/** All fields optional - null means "not filtered on this criterion" (mirrors PolicyFindingFilter). */
public record SecurityFindingFilter(
        Long analysisRunId,
        Long repositoryId,
        SecuritySeverity severity,
        SecurityCategory category,
        String ruleId,
        Instant from,
        Instant to) {
}
