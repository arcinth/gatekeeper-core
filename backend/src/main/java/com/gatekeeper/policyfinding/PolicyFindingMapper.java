package com.gatekeeper.policyfinding;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.policy.PolicyFinding;

/**
 * Converts the frozen PolicyFinding record into the persistable PolicyFindingEntity.
 * Public, not package-private, since Sprint 3 Milestone 2 (ADR-025) - this is
 * now called from com.gatekeeper.orchestration.AnalysisResultPersistenceService,
 * a different package. See com.gatekeeper.securityfinding.SecurityFindingMapper
 * for the Security Engine's equivalent, designed public from the start for the
 * same reason.
 */
public final class PolicyFindingMapper {

    private PolicyFindingMapper() {
    }

    public static PolicyFindingEntity toEntity(AnalysisRun analysisRun, PolicyFinding finding) {
        return PolicyFindingEntity.builder()
                .analysisRun(analysisRun)
                .ruleId(finding.ruleId())
                .category(finding.category())
                .severity(finding.severity())
                .filePath(finding.filePath())
                .lineNumber(finding.lineNumber())
                .message(finding.message())
                .recommendation(finding.recommendation())
                .build();
    }
}
