package com.gatekeeper.analysisrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AnalysisRunServiceTest {

    private static final String COMMIT_SHA = "abc123def456";

    private final AnalysisRunRepository analysisRunRepository = mock(AnalysisRunRepository.class);
    private final PolicyFindingRepository policyFindingRepository = mock(PolicyFindingRepository.class);
    private final SecurityFindingRepository securityFindingRepository = mock(SecurityFindingRepository.class);
    private final AnalysisRunService service =
            new AnalysisRunService(analysisRunRepository, policyFindingRepository, securityFindingRepository);
    private final PullRequest pullRequest = pullRequestWithId(42L);

    @Test
    void createIfAbsent_createsARunInReceivedStatusWhenNoneExistsForThisCommit() {
        when(analysisRunRepository.findByPullRequestIdAndCommitSha(42L, COMMIT_SHA)).thenReturn(Optional.empty());
        when(analysisRunRepository.saveAndFlush(any(AnalysisRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnalysisRun result = service.createIfAbsent(pullRequest, COMMIT_SHA, AnalysisRunTriggerReason.OPENED);

        assertThat(result.getPullRequest()).isEqualTo(pullRequest);
        assertThat(result.getCommitSha()).isEqualTo(COMMIT_SHA);
        assertThat(result.getTriggerReason()).isEqualTo(AnalysisRunTriggerReason.OPENED);
        assertThat(result.getStatus()).isEqualTo(AnalysisRunStatus.RECEIVED);
    }

    @Test
    void createIfAbsent_returnsTheExistingRunWithoutInsertingWhenOneAlreadyExistsForThisCommit() {
        AnalysisRun existing = AnalysisRun.builder()
                .pullRequest(pullRequest)
                .commitSha(COMMIT_SHA)
                .triggerReason(AnalysisRunTriggerReason.OPENED)
                .status(AnalysisRunStatus.RECEIVED)
                .build();
        when(analysisRunRepository.findByPullRequestIdAndCommitSha(42L, COMMIT_SHA)).thenReturn(Optional.of(existing));

        AnalysisRun result = service.createIfAbsent(pullRequest, COMMIT_SHA, AnalysisRunTriggerReason.SYNCHRONIZE);

        assertThat(result).isSameAs(existing);
        verify(analysisRunRepository, never()).saveAndFlush(any());
    }

    @Test
    void createIfAbsent_isIdempotentAcrossRepeatedCallsForTheSameCommit() {
        when(analysisRunRepository.findByPullRequestIdAndCommitSha(42L, COMMIT_SHA)).thenReturn(Optional.empty());
        when(analysisRunRepository.saveAndFlush(any(AnalysisRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createIfAbsent(pullRequest, COMMIT_SHA, AnalysisRunTriggerReason.OPENED);
        // A redelivered webhook for the same commit must not create a second run -
        // simulated here by the repository now reporting the run as existing.
        AnalysisRun firstRun = AnalysisRun.builder().pullRequest(pullRequest).commitSha(COMMIT_SHA).build();
        when(analysisRunRepository.findByPullRequestIdAndCommitSha(42L, COMMIT_SHA)).thenReturn(Optional.of(firstRun));

        AnalysisRun second = service.createIfAbsent(pullRequest, COMMIT_SHA, AnalysisRunTriggerReason.OPENED);

        assertThat(second).isSameAs(firstRun);
        verify(analysisRunRepository, never()).save(any());
    }

    @Test
    void createIfAbsent_createsSeparateRunsForDifferentCommitsOnTheSamePullRequest() {
        when(analysisRunRepository.findByPullRequestIdAndCommitSha(42L, "sha-one")).thenReturn(Optional.empty());
        when(analysisRunRepository.findByPullRequestIdAndCommitSha(42L, "sha-two")).thenReturn(Optional.empty());
        when(analysisRunRepository.saveAndFlush(any(AnalysisRun.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AnalysisRun first = service.createIfAbsent(pullRequest, "sha-one", AnalysisRunTriggerReason.OPENED);
        AnalysisRun second = service.createIfAbsent(pullRequest, "sha-two", AnalysisRunTriggerReason.SYNCHRONIZE);

        assertThat(first.getCommitSha()).isEqualTo("sha-one");
        assertThat(second.getCommitSha()).isEqualTo("sha-two");
    }

    @Test
    void createIfAbsent_recoversWhenAConcurrentDeliveryInsertsTheSameCommitFirst() {
        AnalysisRun wonTheRace = AnalysisRun.builder()
                .pullRequest(pullRequest)
                .commitSha(COMMIT_SHA)
                .triggerReason(AnalysisRunTriggerReason.OPENED)
                .status(AnalysisRunStatus.RECEIVED)
                .build();
        when(analysisRunRepository.findByPullRequestIdAndCommitSha(42L, COMMIT_SHA))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(wonTheRace));
        when(analysisRunRepository.saveAndFlush(any(AnalysisRun.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        AnalysisRun result = service.createIfAbsent(pullRequest, COMMIT_SHA, AnalysisRunTriggerReason.SYNCHRONIZE);

        assertThat(result).isSameAs(wonTheRace);
    }

    @Test
    void findByIdOrThrow_returnsTheRunWhenItExists() {
        AnalysisRun run = AnalysisRun.builder().pullRequest(pullRequest).build();
        when(analysisRunRepository.findById(9L)).thenReturn(Optional.of(run));

        assertThat(service.findByIdOrThrow(9L)).isSameAs(run);
    }

    @Test
    void findByIdOrThrow_throwsResourceNotFoundExceptionWhenMissing() {
        when(analysisRunRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByIdOrThrow(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markQueued_transitionsAnAlreadyLoadedRunToQueuedAndSavesIt() {
        AnalysisRun run = AnalysisRun.builder().pullRequest(pullRequest).status(AnalysisRunStatus.RECEIVED).build();
        when(analysisRunRepository.save(run)).thenReturn(run);

        AnalysisRun result = service.markQueued(run);

        assertThat(result.getStatus()).isEqualTo(AnalysisRunStatus.QUEUED);
        verify(analysisRunRepository).save(run);
    }

    @Test
    void markInProgress_loadsWithTheEagerAssociationGraphAndTransitionsToInProgress() {
        AnalysisRun run = AnalysisRun.builder().pullRequest(pullRequest).status(AnalysisRunStatus.QUEUED).build();
        when(analysisRunRepository.findWithPullRequestAndRepositoryById(9L)).thenReturn(Optional.of(run));
        when(analysisRunRepository.save(run)).thenReturn(run);

        AnalysisRun result = service.markInProgress(9L);

        assertThat(result.getStatus()).isEqualTo(AnalysisRunStatus.IN_PROGRESS);
    }

    @Test
    void markInProgress_throwsResourceNotFoundExceptionWhenMissing() {
        when(analysisRunRepository.findWithPullRequestAndRepositoryById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markInProgress(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findWithPullRequestAndRepositoryByIdOrThrow_loadsWithTheEagerAssociationGraphWithoutTouchingStatus() {
        AnalysisRun run = AnalysisRun.builder().pullRequest(pullRequest).status(AnalysisRunStatus.QUEUED).build();
        when(analysisRunRepository.findWithPullRequestAndRepositoryById(9L)).thenReturn(Optional.of(run));

        AnalysisRun result = service.findWithPullRequestAndRepositoryByIdOrThrow(9L);

        assertThat(result).isSameAs(run);
        assertThat(result.getStatus()).isEqualTo(AnalysisRunStatus.QUEUED);
        verify(analysisRunRepository, never()).save(any());
    }

    @Test
    void findWithPullRequestAndRepositoryByIdOrThrow_throwsResourceNotFoundExceptionWhenMissing() {
        when(analysisRunRepository.findWithPullRequestAndRepositoryById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findWithPullRequestAndRepositoryByIdOrThrow(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void markCompleted_transitionsAnAlreadyLoadedRunToCompleted() {
        AnalysisRun run = AnalysisRun.builder().pullRequest(pullRequest).status(AnalysisRunStatus.IN_PROGRESS).build();
        when(analysisRunRepository.save(run)).thenReturn(run);

        service.markCompleted(run);

        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
        verify(analysisRunRepository).save(run);
    }

    @Test
    void markFailed_loadsByIdAndRecordsTheFailureReason() {
        AnalysisRun run = AnalysisRun.builder().pullRequest(pullRequest).status(AnalysisRunStatus.IN_PROGRESS).build();
        when(analysisRunRepository.findById(9L)).thenReturn(Optional.of(run));
        when(analysisRunRepository.save(run)).thenReturn(run);

        service.markFailed(9L, "GITHUB_API_ERROR: rate limited");

        assertThat(run.getStatus()).isEqualTo(AnalysisRunStatus.FAILED);
        assertThat(run.getFailureReason()).isEqualTo("GITHUB_API_ERROR: rate limited");
    }

    @Test
    void markFailed_truncatesAnOverlyLongReasonToFitTheColumn() {
        AnalysisRun run = AnalysisRun.builder().pullRequest(pullRequest).status(AnalysisRunStatus.IN_PROGRESS).build();
        when(analysisRunRepository.findById(9L)).thenReturn(Optional.of(run));
        when(analysisRunRepository.save(run)).thenReturn(run);
        String tooLong = "x".repeat(2500);

        service.markFailed(9L, tooLong);

        assertThat(run.getFailureReason()).hasSize(2000);
    }

    @Test
    void findSummaryPage_enrichesEachRowWithItsFindingsTotalsFromBothEnginesBatchedCountQueries() {
        PullRequest pr = pullRequestWithRepository(42L, 100L, "org/core");
        AnalysisRun run = AnalysisRun.builder().pullRequest(pr).commitSha(COMMIT_SHA)
                .status(AnalysisRunStatus.COMPLETED).triggerReason(AnalysisRunTriggerReason.OPENED).build();
        org.springframework.test.util.ReflectionTestUtils.setField(run, "id", 9L);
        org.springframework.data.domain.Page<AnalysisRun> page =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(run));
        when(analysisRunRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<AnalysisRun>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);
        when(policyFindingRepository.countByAnalysisRunIdIn(java.util.List.of(9L)))
                .thenReturn(java.util.List.<Object[]>of(new Object[] {9L, 3L}));
        when(securityFindingRepository.countByAnalysisRunIdIn(java.util.List.of(9L)))
                .thenReturn(java.util.List.<Object[]>of(new Object[] {9L, 2L}));

        var result = service.findSummaryPage(
                new com.gatekeeper.analysisrun.dto.AnalysisRunFilter(null, null, null, null, null),
                org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).findingsTotal()).isEqualTo(3L);
        assertThat(result.getContent().get(0).securityFindingsTotal()).isEqualTo(2L);
        assertThat(result.getContent().get(0).repositoryFullName()).isEqualTo("org/core");
    }

    @Test
    void findSummaryPage_defaultsBothFindingsTotalsToZeroWhenNoCountRowExists() {
        PullRequest pr = pullRequestWithRepository(42L, 100L, "org/core");
        AnalysisRun run = AnalysisRun.builder().pullRequest(pr).commitSha(COMMIT_SHA)
                .status(AnalysisRunStatus.COMPLETED).triggerReason(AnalysisRunTriggerReason.OPENED).build();
        org.springframework.test.util.ReflectionTestUtils.setField(run, "id", 9L);
        org.springframework.data.domain.Page<AnalysisRun> page =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(run));
        when(analysisRunRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<AnalysisRun>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);
        when(policyFindingRepository.countByAnalysisRunIdIn(java.util.List.of(9L))).thenReturn(java.util.List.of());
        when(securityFindingRepository.countByAnalysisRunIdIn(java.util.List.of(9L))).thenReturn(java.util.List.of());

        var result = service.findSummaryPage(
                new com.gatekeeper.analysisrun.dto.AnalysisRunFilter(null, null, null, null, null),
                org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result.getContent().get(0).findingsTotal()).isZero();
        assertThat(result.getContent().get(0).securityFindingsTotal()).isZero();
    }

    @Test
    void findSummaryPage_skipsBothBatchCountQueriesEntirelyWhenThePageIsEmpty() {
        org.springframework.data.domain.Page<AnalysisRun> emptyPage =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of());
        when(analysisRunRepository.findAll(
                org.mockito.ArgumentMatchers.<org.springframework.data.jpa.domain.Specification<AnalysisRun>>any(),
                org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(emptyPage);

        var result = service.findSummaryPage(
                new com.gatekeeper.analysisrun.dto.AnalysisRunFilter(null, null, null, null, null),
                org.springframework.data.domain.PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        verify(policyFindingRepository, never()).countByAnalysisRunIdIn(any());
        verify(securityFindingRepository, never()).countByAnalysisRunIdIn(any());
    }

    @Test
    void findDetailByIdOrThrow_assemblesTheDetailResponseWithFindingsBySeverityFromBothEngines() {
        PullRequest pr = pullRequestWithRepository(42L, 100L, "org/core");
        AnalysisRun run = AnalysisRun.builder().pullRequest(pr).commitSha(COMMIT_SHA)
                .status(AnalysisRunStatus.COMPLETED).triggerReason(AnalysisRunTriggerReason.OPENED).build();
        org.springframework.test.util.ReflectionTestUtils.setField(run, "id", 9L);
        when(analysisRunRepository.findWithPullRequestAndRepositoryById(9L)).thenReturn(Optional.of(run));
        when(policyFindingRepository.countBySeverityForAnalysisRun(9L))
                .thenReturn(java.util.List.<Object[]>of(new Object[] {com.gatekeeper.policy.PolicySeverity.HIGH, 2L}));
        when(securityFindingRepository.countBySeverityForAnalysisRun(9L))
                .thenReturn(java.util.List.<Object[]>of(
                        new Object[] {com.gatekeeper.securityengine.SecuritySeverity.CRITICAL, 1L}));

        var result = service.findDetailByIdOrThrow(9L);

        assertThat(result.id()).isEqualTo(9L);
        assertThat(result.repository().fullName()).isEqualTo("org/core");
        assertThat(result.findingsBySeverity()).containsEntry(com.gatekeeper.policy.PolicySeverity.HIGH, 2L);
        assertThat(result.securityFindingsBySeverity())
                .containsEntry(com.gatekeeper.securityengine.SecuritySeverity.CRITICAL, 1L);
    }

    @Test
    void findDetailByIdOrThrow_throwsResourceNotFoundExceptionWhenMissing() {
        when(analysisRunRepository.findWithPullRequestAndRepositoryById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetailByIdOrThrow(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private PullRequest pullRequestWithId(Long id) {
        PullRequest pr = PullRequest.builder().build();
        // BaseEntity's id is generated, not settable via the builder - reflection
        // is the only way to give this in-memory test object a stable id to key on.
        org.springframework.test.util.ReflectionTestUtils.setField(pr, "id", id);
        return pr;
    }

    private PullRequest pullRequestWithRepository(Long prId, Long repositoryId, String repositoryFullName) {
        com.gatekeeper.repository.Repository repository = com.gatekeeper.repository.Repository.builder()
                .name("core")
                .fullName(repositoryFullName)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(repository, "id", repositoryId);
        PullRequest pr = PullRequest.builder()
                .repository(repository)
                .number(21)
                .title("Add example")
                .authorLogin("octocat")
                .sourceBranch("feature")
                .targetBranch("main")
                .headSha("sha")
                .status(com.gatekeeper.pullrequest.PullRequestStatus.OPEN)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(pr, "id", prId);
        return pr;
    }
}
