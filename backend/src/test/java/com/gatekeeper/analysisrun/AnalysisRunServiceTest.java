package com.gatekeeper.analysisrun;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.pullrequest.PullRequest;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AnalysisRunServiceTest {

    private static final String COMMIT_SHA = "abc123def456";

    private final AnalysisRunRepository analysisRunRepository = mock(AnalysisRunRepository.class);
    private final AnalysisRunService service = new AnalysisRunService(analysisRunRepository);
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

    private PullRequest pullRequestWithId(Long id) {
        PullRequest pr = PullRequest.builder().build();
        // BaseEntity's id is generated, not settable via the builder - reflection
        // is the only way to give this in-memory test object a stable id to key on.
        org.springframework.test.util.ReflectionTestUtils.setField(pr, "id", id);
        return pr;
    }
}
