package com.gatekeeper.policyfinding;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.policy.PolicyFinding;

/** Converts the frozen PolicyFinding record into the persistable PolicyFindingEntity. */
final class PolicyFindingMapper {

    private PolicyFindingMapper() {
    }

    static PolicyFindingEntity toEntity(AnalysisRun analysisRun, PolicyFinding finding) {
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
