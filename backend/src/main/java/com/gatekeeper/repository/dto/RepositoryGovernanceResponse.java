package com.gatekeeper.repository.dto;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.report.AiReviewStatus;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.util.Map;

/**
 * The Dashboard's own overview shape (DashboardOverviewResponse), scoped
 * down to one repository (Repository Governance View Architecture, Section
 * 5/7) - repository identity plus the same six aggregate shapes, computed
 * live on every request, never persisted or cached (ADR-051). A deliberate
 * sibling of DashboardOverviewResponse, not a shared/reused type (ADR-052):
 * this carries a single repository's identity instead of organization-wide
 * totalRepositories, and the two are expected to evolve independently.
 */
public record RepositoryGovernanceResponse(
        Long repositoryId,
        String repositoryFullName,
        int windowDays,
        long totalAnalysisRuns,
        Map<AnalysisRunStatus, Long> runsByStatus,
        long totalFindings,
        Map<PolicySeverity, Long> findingsBySeverity,
        Map<PolicyCategory, Long> findingsByCategory,
        long totalSecurityFindings,
        Map<SecuritySeverity, Long> securityFindingsBySeverity,
        Map<SecurityCategory, Long> securityFindingsByCategory,
        long totalAiReviewFindings,
        Map<AIReviewConfidence, Long> aiReviewFindingsByConfidence,
        Map<AIReviewFindingType, Long> aiReviewFindingsByType,
        long totalVerdicts,
        Map<VerdictOutcome, Long> verdictsByOutcome,
        long totalReportsPublished,
        Map<AiReviewStatus, Long> reportsByAiStatus) {
}
