package com.gatekeeper.securityfinding;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.securityengine.SecurityFinding;

/**
 * Converts the frozen SecurityFinding record into the persistable
 * SecurityFindingEntity. Mirrors com.gatekeeper.policyfinding.PolicyFindingMapper,
 * with one deliberate difference: public, not package-private, because it is
 * called from com.gatekeeper.orchestration.AnalysisResultPersistenceService -
 * a different package - per the approved integration design (ADR-025).
 * PolicyFindingMapper's own visibility was relaxed to public for the same
 * reason when that service was introduced.
 */
public final class SecurityFindingMapper {

    private SecurityFindingMapper() {
    }

    public static SecurityFindingEntity toEntity(AnalysisRun analysisRun, SecurityFinding finding) {
        return SecurityFindingEntity.builder()
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
