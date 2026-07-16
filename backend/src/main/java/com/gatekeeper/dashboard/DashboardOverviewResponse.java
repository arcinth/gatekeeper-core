package com.gatekeeper.dashboard;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.util.Map;

/**
 * totalAiReviewRuns/aiReviewRunsByStatus were added alongside the existing
 * Policy/Security aggregates (Sprint 4 Milestone 4) - AI Review is the only
 * engine with its own independent run-level lifecycle (COMPLETED/FAILED,
 * distinct from AnalysisRun's own status - see AIReviewRun's Javadoc), so
 * its dashboard section surfaces both run outcomes and finding counts,
 * unlike Policy/Security which only ever surface finding counts.
 */
public record DashboardOverviewResponse(
        int windowDays,
        long totalRepositories,
        long totalAnalysisRuns,
        Map<AnalysisRunStatus, Long> runsByStatus,
        long totalFindings,
        Map<PolicySeverity, Long> findingsBySeverity,
        Map<PolicyCategory, Long> findingsByCategory,
        long totalSecurityFindings,
        Map<SecuritySeverity, Long> securityFindingsBySeverity,
        Map<SecurityCategory, Long> securityFindingsByCategory,
        long totalAiReviewRuns,
        Map<AIReviewRunStatus, Long> aiReviewRunsByStatus,
        long totalAiReviewFindings,
        Map<AIReviewConfidence, Long> aiReviewFindingsByConfidence,
        Map<AIReviewFindingType, Long> aiReviewFindingsByType) {
}
