package com.gatekeeper.dashboard;

import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.util.Map;

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
        Map<SecurityCategory, Long> securityFindingsByCategory) {
}
