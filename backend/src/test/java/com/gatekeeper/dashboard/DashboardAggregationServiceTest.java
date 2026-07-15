package com.gatekeeper.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.repository.RepositoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardAggregationServiceTest {

    private final AnalysisRunRepository analysisRunRepository = mock(AnalysisRunRepository.class);
    private final PolicyFindingRepository policyFindingRepository = mock(PolicyFindingRepository.class);
    private final RepositoryRepository repositoryRepository = mock(RepositoryRepository.class);
    private final DashboardAggregationService service =
            new DashboardAggregationService(analysisRunRepository, policyFindingRepository, repositoryRepository);

    @Test
    void getOverview_assemblesCountsFromEachAggregateQuery() {
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

        DashboardOverviewResponse overview = service.getOverview(30);

        assertThat(overview.windowDays()).isEqualTo(30);
        assertThat(overview.totalRepositories()).isEqualTo(5L);
        assertThat(overview.totalAnalysisRuns()).isEqualTo(12L);
        assertThat(overview.runsByStatus()).containsEntry(AnalysisRunStatus.COMPLETED, 10L);
        assertThat(overview.totalFindings()).isEqualTo(8L);
        assertThat(overview.findingsBySeverity()).containsEntry(PolicySeverity.HIGH, 3L);
        assertThat(overview.findingsByCategory()).containsEntry(PolicyCategory.CODE_QUALITY, 3L);
    }

    @Test
    void getOverview_defaultsToA30DayWindowWhenNoneIsRequested() {
        when(repositoryRepository.count()).thenReturn(0L);
        when(analysisRunRepository.countByStatusSince(any())).thenReturn(List.of());
        when(policyFindingRepository.countBySeveritySince(any())).thenReturn(List.of());
        when(policyFindingRepository.countByCategorySince(any())).thenReturn(List.of());

        DashboardOverviewResponse overview = service.getOverview(null);

        assertThat(overview.windowDays()).isEqualTo(30);
    }

    @Test
    void getOverview_returnsZeroTotalsWhenThereIsNoData() {
        when(repositoryRepository.count()).thenReturn(0L);
        when(analysisRunRepository.countByStatusSince(any())).thenReturn(List.of());
        when(policyFindingRepository.countBySeveritySince(any())).thenReturn(List.of());
        when(policyFindingRepository.countByCategorySince(any())).thenReturn(List.of());

        DashboardOverviewResponse overview = service.getOverview(7);

        assertThat(overview.totalAnalysisRuns()).isZero();
        assertThat(overview.totalFindings()).isZero();
        assertThat(overview.runsByStatus()).isEmpty();
    }
}
