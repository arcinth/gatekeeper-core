package com.gatekeeper.dashboard;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.report.AiReviewStatus;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.util.Map;

/**
 * totalAiReviewRuns/aiReviewRunsByStatus were added alongside the existing
 * Policy/Security aggregates (Sprint 4 Milestone 4) - AI Review is the only
 * engine with its own independent run-level lifecycle (COMPLETED/FAILED,
 * distinct from AnalysisRun's own status - see AIReviewRun's Javadoc), so
 * its dashboard section surfaces both run outcomes and finding counts,
 * unlike Policy/Security which only ever surface finding counts.
 * <p>
 * totalVerdicts/verdictsByOutcome (Sprint 5 Milestone 3) is the platform's
 * first genuinely governance-shaped metric - a block rate
 * (verdictsByOutcome.BLOCKED / totalVerdicts) - deliberately meant to read as
 * the dashboard's headline number, not just another engine's finding count
 * (Sprint 5 Architecture, Section 15).
 * <p>
 * totalReportsPublished/reportsByAiStatus (Unified Engineering Report
 * Architecture, Section 11) is added the same additive way, alongside
 * totalVerdicts rather than replacing or renaming it - a published report is
 * a distinct fact from a produced verdict (a report can lag its verdict
 * while it waits on AI review, per Section 6), so both counts are kept.
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
        Map<AIReviewFindingType, Long> aiReviewFindingsByType,
        long totalVerdicts,
        Map<VerdictOutcome, Long> verdictsByOutcome,
        long totalReportsPublished,
        Map<AiReviewStatus, Long> reportsByAiStatus) {
}
