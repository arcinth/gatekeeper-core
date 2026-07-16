package com.gatekeeper.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewfinding.AIReviewFindingRepository;
import com.gatekeeper.aireviewrun.AIReviewRunRepository;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.report.AiReviewStatus;
import com.gatekeeper.report.EngineeringReportRepository;
import com.gatekeeper.repository.RepositoryRepository;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardAggregationServiceTest {

    private final AnalysisRunRepository analysisRunRepository = mock(AnalysisRunRepository.class);
    private final PolicyFindingRepository policyFindingRepository = mock(PolicyFindingRepository.class);
    private final SecurityFindingRepository securityFindingRepository = mock(SecurityFindingRepository.class);
    private final AIReviewRunRepository aiReviewRunRepository = mock(AIReviewRunRepository.class);
    private final AIReviewFindingRepository aiReviewFindingRepository = mock(AIReviewFindingRepository.class);
    private final VerdictRepository verdictRepository = mock(VerdictRepository.class);
    private final EngineeringReportRepository engineeringReportRepository = mock(EngineeringReportRepository.class);
    private final RepositoryRepository repositoryRepository = mock(RepositoryRepository.class);
    private final DashboardAggregationService service = new DashboardAggregationService(
            analysisRunRepository, policyFindingRepository, securityFindingRepository,
            aiReviewRunRepository, aiReviewFindingRepository, verdictRepository, engineeringReportRepository,
            repositoryRepository);

    @Test
    void getOverview_assemblesCountsFromEachAggregateQueryIncludingSecurityAndAiReviewFindings() {
        when(repositoryRepository.count()).thenReturn(5L);
        when(analysisRunRepository.countByStatusSince(any())).thenReturn(List.of(
                new Object[] {AnalysisRunStatus.COMPLETED, 10L},
                new Object[] {AnalysisRunStatus.FAILED, 2L}));
        when(policyFindingRepository.countBySeveritySince(any())).thenReturn(List.of(
                new Object[] {PolicySeverity.LOW, 5L},
                new Object[] {PolicySeverity.HIGH, 3L}));
        when(policyFindingRepository.countByCategorySince(any())).thenReturn(List.of(
                new Object[] {PolicyCategory.MAINTAINABILITY, 5L},
                new Object[] {PolicyCategory.CODE_QUALITY, 3L}));
        when(securityFindingRepository.countBySeveritySince(any())).thenReturn(List.of(
                new Object[] {SecuritySeverity.CRITICAL, 1L},
                new Object[] {SecuritySeverity.HIGH, 4L}));
        when(securityFindingRepository.countByCategorySince(any())).thenReturn(List.of(
                new Object[] {SecurityCategory.SECRETS_EXPOSURE, 1L},
                new Object[] {SecurityCategory.INSECURE_CRYPTOGRAPHY, 4L}));
        when(aiReviewRunRepository.countByStatusSince(any())).thenReturn(List.of(
                new Object[] {AIReviewRunStatus.COMPLETED, 6L},
                new Object[] {AIReviewRunStatus.FAILED, 1L}));
        when(aiReviewFindingRepository.countByConfidenceSince(any())).thenReturn(List.of(
                new Object[] {AIReviewConfidence.HIGH, 2L},
                new Object[] {AIReviewConfidence.LOW, 3L}));
        when(aiReviewFindingRepository.countByTypeSince(any())).thenReturn(List.of(
                new Object[] {AIReviewFindingType.POTENTIAL_BUG, 2L},
                new Object[] {AIReviewFindingType.SUGGESTION, 3L}));
        when(verdictRepository.countByOutcomeSince(any())).thenReturn(List.of(
                new Object[] {VerdictOutcome.APPROVED, 9L},
                new Object[] {VerdictOutcome.BLOCKED, 3L}));
        when(engineeringReportRepository.countByAiReviewStatusSince(any())).thenReturn(List.of(
                new Object[] {AiReviewStatus.INCLUDED, 7L},
                new Object[] {AiReviewStatus.UNAVAILABLE, 2L}));

        DashboardOverviewResponse overview = service.getOverview(30);

        assertThat(overview.windowDays()).isEqualTo(30);
        assertThat(overview.totalRepositories()).isEqualTo(5L);
        assertThat(overview.totalAnalysisRuns()).isEqualTo(12L);
        assertThat(overview.runsByStatus()).containsEntry(AnalysisRunStatus.COMPLETED, 10L);
        assertThat(overview.totalFindings()).isEqualTo(8L);
        assertThat(overview.findingsBySeverity()).containsEntry(PolicySeverity.HIGH, 3L);
        assertThat(overview.findingsByCategory()).containsEntry(PolicyCategory.CODE_QUALITY, 3L);
        assertThat(overview.totalSecurityFindings()).isEqualTo(5L);
        assertThat(overview.securityFindingsBySeverity()).containsEntry(SecuritySeverity.CRITICAL, 1L);
        assertThat(overview.securityFindingsByCategory()).containsEntry(SecurityCategory.INSECURE_CRYPTOGRAPHY, 4L);
        assertThat(overview.totalAiReviewRuns()).isEqualTo(7L);
        assertThat(overview.aiReviewRunsByStatus()).containsEntry(AIReviewRunStatus.COMPLETED, 6L);
        assertThat(overview.totalAiReviewFindings()).isEqualTo(5L);
        assertThat(overview.aiReviewFindingsByConfidence()).containsEntry(AIReviewConfidence.HIGH, 2L);
        assertThat(overview.aiReviewFindingsByType()).containsEntry(AIReviewFindingType.SUGGESTION, 3L);
        assertThat(overview.totalVerdicts()).isEqualTo(12L);
        assertThat(overview.verdictsByOutcome()).containsEntry(VerdictOutcome.APPROVED, 9L)
                .containsEntry(VerdictOutcome.BLOCKED, 3L);
        assertThat(overview.totalReportsPublished()).isEqualTo(9L);
        assertThat(overview.reportsByAiStatus()).containsEntry(AiReviewStatus.INCLUDED, 7L)
                .containsEntry(AiReviewStatus.UNAVAILABLE, 2L);
    }

    @Test
    void getOverview_defaultsToA30DayWindowWhenNoneIsRequested() {
        when(repositoryRepository.count()).thenReturn(0L);
        when(analysisRunRepository.countByStatusSince(any())).thenReturn(List.of());
        when(policyFindingRepository.countBySeveritySince(any())).thenReturn(List.of());
        when(policyFindingRepository.countByCategorySince(any())).thenReturn(List.of());
        when(securityFindingRepository.countBySeveritySince(any())).thenReturn(List.of());
        when(securityFindingRepository.countByCategorySince(any())).thenReturn(List.of());
        when(aiReviewRunRepository.countByStatusSince(any())).thenReturn(List.of());
        when(aiReviewFindingRepository.countByConfidenceSince(any())).thenReturn(List.of());
        when(aiReviewFindingRepository.countByTypeSince(any())).thenReturn(List.of());
        when(verdictRepository.countByOutcomeSince(any())).thenReturn(List.of());
        when(engineeringReportRepository.countByAiReviewStatusSince(any())).thenReturn(List.of());

        DashboardOverviewResponse overview = service.getOverview(null);

        assertThat(overview.windowDays()).isEqualTo(30);
    }

    @Test
    void getOverview_returnsZeroTotalsWhenThereIsNoData() {
        when(repositoryRepository.count()).thenReturn(0L);
        when(analysisRunRepository.countByStatusSince(any())).thenReturn(List.of());
        when(policyFindingRepository.countBySeveritySince(any())).thenReturn(List.of());
        when(policyFindingRepository.countByCategorySince(any())).thenReturn(List.of());
        when(securityFindingRepository.countBySeveritySince(any())).thenReturn(List.of());
        when(securityFindingRepository.countByCategorySince(any())).thenReturn(List.of());
        when(aiReviewRunRepository.countByStatusSince(any())).thenReturn(List.of());
        when(aiReviewFindingRepository.countByConfidenceSince(any())).thenReturn(List.of());
        when(aiReviewFindingRepository.countByTypeSince(any())).thenReturn(List.of());
        when(verdictRepository.countByOutcomeSince(any())).thenReturn(List.of());
        when(engineeringReportRepository.countByAiReviewStatusSince(any())).thenReturn(List.of());

        DashboardOverviewResponse overview = service.getOverview(7);

        assertThat(overview.totalAnalysisRuns()).isZero();
        assertThat(overview.totalFindings()).isZero();
        assertThat(overview.runsByStatus()).isEmpty();
        assertThat(overview.totalSecurityFindings()).isZero();
        assertThat(overview.totalAiReviewRuns()).isZero();
        assertThat(overview.totalAiReviewFindings()).isZero();
        assertThat(overview.totalVerdicts()).isZero();
        assertThat(overview.totalReportsPublished()).isZero();
    }
}
