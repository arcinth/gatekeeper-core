package com.gatekeeper.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewfinding.AIReviewFindingRepository;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.github.GitHubInstallationRepository;
import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.report.AiReviewStatus;
import com.gatekeeper.report.EngineeringReportRepository;
import com.gatekeeper.repository.dto.RepositoryGovernanceResponse;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class RepositoryGovernanceServiceTest {

    private static final Long REPOSITORY_ID = 9L;

    private final RepositoryRepository repositoryRepository = mock(RepositoryRepository.class);
    private final GitHubInstallationRepository gitHubInstallationRepository = mock(GitHubInstallationRepository.class);
    private final OrganizationService organizationService = mock(OrganizationService.class);
    private final RepositoryService repositoryService =
            new RepositoryService(repositoryRepository, gitHubInstallationRepository, organizationService);
    private final AnalysisRunRepository analysisRunRepository = mock(AnalysisRunRepository.class);
    private final PolicyFindingRepository policyFindingRepository = mock(PolicyFindingRepository.class);
    private final SecurityFindingRepository securityFindingRepository = mock(SecurityFindingRepository.class);
    private final AIReviewFindingRepository aiReviewFindingRepository = mock(AIReviewFindingRepository.class);
    private final VerdictRepository verdictRepository = mock(VerdictRepository.class);
    private final EngineeringReportRepository engineeringReportRepository = mock(EngineeringReportRepository.class);
    private final RepositoryGovernanceService service = new RepositoryGovernanceService(
            repositoryService, analysisRunRepository, policyFindingRepository, securityFindingRepository,
            aiReviewFindingRepository, verdictRepository, engineeringReportRepository);

    @Test
    void getGovernanceSummary_throwsResourceNotFoundForAnUnknownRepository() {
        when(repositoryRepository.findById(404L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.getGovernanceSummary(404L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getGovernanceSummary_assemblesCountsFromEveryAggregateQueryScopedToTheRepository() {
        stubRepository();
        when(analysisRunRepository.countByStatusSinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.of(
                new Object[] {AnalysisRunStatus.COMPLETED, 5L},
                new Object[] {AnalysisRunStatus.FAILED, 1L}));
        when(policyFindingRepository.countBySeveritySinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.<Object[]>of(
                new Object[] {PolicySeverity.LOW, 3L}));
        when(policyFindingRepository.countByCategorySinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.<Object[]>of(
                new Object[] {PolicyCategory.MAINTAINABILITY, 3L}));
        when(securityFindingRepository.countBySeveritySinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.<Object[]>of(
                new Object[] {SecuritySeverity.CRITICAL, 1L}));
        when(securityFindingRepository.countByCategorySinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.<Object[]>of(
                new Object[] {SecurityCategory.SECRETS_EXPOSURE, 1L}));
        when(aiReviewFindingRepository.countByConfidenceSinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.<Object[]>of(
                new Object[] {AIReviewConfidence.HIGH, 2L}));
        when(aiReviewFindingRepository.countByTypeSinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.<Object[]>of(
                new Object[] {AIReviewFindingType.POTENTIAL_BUG, 2L}));
        when(verdictRepository.countByOutcomeSinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.of(
                new Object[] {VerdictOutcome.APPROVED, 4L},
                new Object[] {VerdictOutcome.BLOCKED, 1L}));
        when(engineeringReportRepository.countByAiReviewStatusSinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.<Object[]>of(
                new Object[] {AiReviewStatus.INCLUDED, 3L}));

        RepositoryGovernanceResponse response = service.getGovernanceSummary(REPOSITORY_ID, 30);

        assertThat(response.repositoryId()).isEqualTo(REPOSITORY_ID);
        assertThat(response.repositoryFullName()).isEqualTo("org/core");
        assertThat(response.windowDays()).isEqualTo(30);
        assertThat(response.totalAnalysisRuns()).isEqualTo(6L);
        assertThat(response.runsByStatus()).containsEntry(AnalysisRunStatus.COMPLETED, 5L);
        assertThat(response.totalFindings()).isEqualTo(3L);
        assertThat(response.findingsBySeverity()).containsEntry(PolicySeverity.LOW, 3L);
        assertThat(response.findingsByCategory()).containsEntry(PolicyCategory.MAINTAINABILITY, 3L);
        assertThat(response.totalSecurityFindings()).isEqualTo(1L);
        assertThat(response.securityFindingsBySeverity()).containsEntry(SecuritySeverity.CRITICAL, 1L);
        assertThat(response.totalAiReviewFindings()).isEqualTo(2L);
        assertThat(response.aiReviewFindingsByConfidence()).containsEntry(AIReviewConfidence.HIGH, 2L);
        assertThat(response.aiReviewFindingsByType()).containsEntry(AIReviewFindingType.POTENTIAL_BUG, 2L);
        assertThat(response.totalVerdicts()).isEqualTo(5L);
        assertThat(response.verdictsByOutcome()).containsEntry(VerdictOutcome.APPROVED, 4L)
                .containsEntry(VerdictOutcome.BLOCKED, 1L);
        assertThat(response.totalReportsPublished()).isEqualTo(3L);
        assertThat(response.reportsByAiStatus()).containsEntry(AiReviewStatus.INCLUDED, 3L);
    }

    // --- aggregate query wiring: every repository call must be scoped to this repository, not global ---

    @Test
    void getGovernanceSummary_scopesEveryAggregateQueryToTheRequestedRepositoryId() {
        stubRepository();
        stubEmptyAggregates();

        service.getGovernanceSummary(REPOSITORY_ID, 30);

        verify(analysisRunRepository).countByStatusSinceForRepository(any(), eq(REPOSITORY_ID));
        verify(policyFindingRepository).countBySeveritySinceForRepository(any(), eq(REPOSITORY_ID));
        verify(policyFindingRepository).countByCategorySinceForRepository(any(), eq(REPOSITORY_ID));
        verify(securityFindingRepository).countBySeveritySinceForRepository(any(), eq(REPOSITORY_ID));
        verify(securityFindingRepository).countByCategorySinceForRepository(any(), eq(REPOSITORY_ID));
        verify(aiReviewFindingRepository).countByConfidenceSinceForRepository(any(), eq(REPOSITORY_ID));
        verify(aiReviewFindingRepository).countByTypeSinceForRepository(any(), eq(REPOSITORY_ID));
        verify(verdictRepository).countByOutcomeSinceForRepository(any(), eq(REPOSITORY_ID));
        verify(engineeringReportRepository).countByAiReviewStatusSinceForRepository(any(), eq(REPOSITORY_ID));
    }

    @Test
    void getGovernanceSummary_derivesTheSameSinceCutoffForEveryAggregateQuery() {
        stubRepository();
        stubEmptyAggregates();

        service.getGovernanceSummary(REPOSITORY_ID, 7);

        ArgumentCaptor<Instant> sinceCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(verdictRepository).countByOutcomeSinceForRepository(sinceCaptor.capture(), eq(REPOSITORY_ID));
        Instant expectedFloor = Instant.now().minusSeconds(8 * 24 * 60 * 60L);
        assertThat(sinceCaptor.getValue()).isAfter(expectedFloor);
    }

    @Test
    void getGovernanceSummary_defaultsToA30DayWindowWhenNoneIsRequested() {
        stubRepository();
        stubEmptyAggregates();

        RepositoryGovernanceResponse response = service.getGovernanceSummary(REPOSITORY_ID, null);

        assertThat(response.windowDays()).isEqualTo(30);
    }

    @Test
    void getGovernanceSummary_returnsZeroTotalsWhenThereIsNoDataForThisRepository() {
        stubRepository();
        stubEmptyAggregates();

        RepositoryGovernanceResponse response = service.getGovernanceSummary(REPOSITORY_ID, 30);

        assertThat(response.totalAnalysisRuns()).isZero();
        assertThat(response.totalFindings()).isZero();
        assertThat(response.totalSecurityFindings()).isZero();
        assertThat(response.totalAiReviewFindings()).isZero();
        assertThat(response.totalVerdicts()).isZero();
        assertThat(response.totalReportsPublished()).isZero();
        assertThat(response.runsByStatus()).isEmpty();
    }

    private void stubRepository() {
        Repository repository = Repository.builder().fullName("org/core").build();
        ReflectionTestUtils.setField(repository, "id", REPOSITORY_ID);
        when(repositoryRepository.findById(REPOSITORY_ID)).thenReturn(java.util.Optional.of(repository));
    }

    private void stubEmptyAggregates() {
        when(analysisRunRepository.countByStatusSinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.of());
        when(policyFindingRepository.countBySeveritySinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.of());
        when(policyFindingRepository.countByCategorySinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.of());
        when(securityFindingRepository.countBySeveritySinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.of());
        when(securityFindingRepository.countByCategorySinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.of());
        when(aiReviewFindingRepository.countByConfidenceSinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.of());
        when(aiReviewFindingRepository.countByTypeSinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.of());
        when(verdictRepository.countByOutcomeSinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.of());
        when(engineeringReportRepository.countByAiReviewStatusSinceForRepository(any(), eq(REPOSITORY_ID))).thenReturn(List.of());
    }
}
