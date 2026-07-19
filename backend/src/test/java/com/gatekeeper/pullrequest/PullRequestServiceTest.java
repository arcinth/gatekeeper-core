package com.gatekeeper.pullrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.verdict.Verdict;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

class PullRequestServiceTest {

    private static final Long GITHUB_PR_ID = 555L;

    private final PullRequestRepository pullRequestRepository = mock(PullRequestRepository.class);
    private final AnalysisRunRepository analysisRunRepository = mock(AnalysisRunRepository.class);
    private final VerdictRepository verdictRepository = mock(VerdictRepository.class);
    private final PullRequestService service =
            new PullRequestService(pullRequestRepository, analysisRunRepository, verdictRepository);
    private final Repository repository = Repository.builder()
            .name("core").owner("gatekeeper").fullName("gatekeeper/core").build();

    @BeforeEach
    void stubSaveToReturnItsArgument() {
        when(pullRequestRepository.save(any(PullRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(pullRequestRepository.saveAndFlush(any(PullRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void upsert_createsANewPullRequestWhenNoneExists() {
        when(pullRequestRepository.findByGithubPrId(GITHUB_PR_ID)).thenReturn(Optional.empty());
        PullRequestUpsertCommand command = openedCommand("Add login page", "abc123");

        PullRequest result = service.upsert(repository, command);

        assertThat(result.getRepository()).isEqualTo(repository);
        assertThat(result.getGithubPrId()).isEqualTo(GITHUB_PR_ID);
        assertThat(result.getTitle()).isEqualTo("Add login page");
        assertThat(result.getHeadSha()).isEqualTo("abc123");
        assertThat(result.getStatus()).isEqualTo(PullRequestStatus.OPEN);
    }

    @Test
    void upsert_updatesTitleAndHeadShaWhenPullRequestAlreadyExists() {
        PullRequest existing = existingOpenPullRequest("Old title", "old-sha");
        when(pullRequestRepository.findByGithubPrId(GITHUB_PR_ID)).thenReturn(Optional.of(existing));

        PullRequest result = service.upsert(repository, openedCommand("New title", "new-sha"));

        assertThat(result).isSameAs(existing);
        assertThat(result.getTitle()).isEqualTo("New title");
        assertThat(result.getHeadSha()).isEqualTo("new-sha");
        verify(pullRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void upsert_calledTwiceWithTheSameDataIsIdempotent() {
        PullRequest existing = existingOpenPullRequest("Add login page", "abc123");
        when(pullRequestRepository.findByGithubPrId(GITHUB_PR_ID)).thenReturn(Optional.of(existing));
        PullRequestUpsertCommand command = openedCommand("Add login page", "abc123");

        service.upsert(repository, command);
        PullRequest second = service.upsert(repository, command);

        assertThat(second.getGithubPrId()).isEqualTo(GITHUB_PR_ID);
        verify(pullRequestRepository, times(2)).save(existing);
        verify(pullRequestRepository, never()).saveAndFlush(any());
    }

    @Test
    void upsert_setsMergedStatusWhenPayloadReportsMerged() {
        when(pullRequestRepository.findByGithubPrId(GITHUB_PR_ID)).thenReturn(Optional.empty());
        PullRequestUpsertCommand command = new PullRequestUpsertCommand(
                GITHUB_PR_ID, 7, "Ship it", "octocat", "feature", "main", "sha1", "closed", true);

        PullRequest result = service.upsert(repository, command);

        assertThat(result.getStatus()).isEqualTo(PullRequestStatus.MERGED);
    }

    @Test
    void upsert_setsClosedStatusWhenPayloadReportsClosedWithoutMerge() {
        when(pullRequestRepository.findByGithubPrId(GITHUB_PR_ID)).thenReturn(Optional.empty());
        PullRequestUpsertCommand command = new PullRequestUpsertCommand(
                GITHUB_PR_ID, 7, "Abandoned", "octocat", "feature", "main", "sha1", "closed", false);

        PullRequest result = service.upsert(repository, command);

        assertThat(result.getStatus()).isEqualTo(PullRequestStatus.CLOSED);
    }

    @Test
    void upsert_recoversWhenAConcurrentDeliveryInsertsTheSamePullRequestFirst() {
        PullRequest wonTheRace = existingOpenPullRequest("Add login page", "abc123");
        when(pullRequestRepository.findByGithubPrId(GITHUB_PR_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(wonTheRace));
        when(pullRequestRepository.saveAndFlush(any(PullRequest.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        PullRequest result = service.upsert(repository, openedCommand("Add login page", "abc123"));

        assertThat(result).isSameAs(wonTheRace);
    }

    @Test
    void findSummaryPage_enrichesEachRowWithItsLatestAnalysisRunStatusAndVerdictOutcome() {
        PullRequest pr = existingOpenPullRequest("Add login page", "abc123");
        ReflectionTestUtils.setField(pr, "id", 42L);
        stubPullRequestPage(pr);
        AnalysisRun latestRun = analysisRunFor(pr, 9L, AnalysisRunStatus.COMPLETED);
        AnalysisRun olderRun = analysisRunFor(pr, 8L, AnalysisRunStatus.COMPLETED);
        when(analysisRunRepository.findByPullRequestIdInOrderByPullRequestIdAscCreatedAtDesc(List.of(42L)))
                .thenReturn(List.of(latestRun, olderRun));
        Verdict verdict = Verdict.builder().analysisRun(latestRun).outcome(VerdictOutcome.BLOCKED).build();
        when(verdictRepository.findByAnalysisRunIdIn(List.of(9L))).thenReturn(List.of(verdict));

        Page<com.gatekeeper.pullrequest.dto.PullRequestSummaryResponse> result =
                service.findSummaryPage(new com.gatekeeper.pullrequest.dto.PullRequestFilter(null, null), PageRequest.of(0, 20));

        var row = result.getContent().get(0);
        assertThat(row.latestAnalysisRunId()).isEqualTo(9L);
        assertThat(row.latestAnalysisRunStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
        assertThat(row.latestVerdictOutcome()).isEqualTo(VerdictOutcome.BLOCKED);
    }

    @Test
    void findSummaryPage_leavesLatestRunFieldsNullWhenNoAnalysisRunExistsYet() {
        PullRequest pr = existingOpenPullRequest("Add login page", "abc123");
        ReflectionTestUtils.setField(pr, "id", 42L);
        stubPullRequestPage(pr);
        when(analysisRunRepository.findByPullRequestIdInOrderByPullRequestIdAscCreatedAtDesc(List.of(42L)))
                .thenReturn(List.of());

        Page<com.gatekeeper.pullrequest.dto.PullRequestSummaryResponse> result =
                service.findSummaryPage(new com.gatekeeper.pullrequest.dto.PullRequestFilter(null, null), PageRequest.of(0, 20));

        var row = result.getContent().get(0);
        assertThat(row.latestAnalysisRunId()).isNull();
        assertThat(row.latestAnalysisRunStatus()).isNull();
        assertThat(row.latestVerdictOutcome()).isNull();
        verify(verdictRepository, never()).findByAnalysisRunIdIn(any());
    }

    @Test
    void findSummaryPage_includesGithubMetadataOnEachRow() {
        PullRequest pr = existingOpenPullRequest("Add login page", "abc123");
        ReflectionTestUtils.setField(pr, "id", 42L);
        stubPullRequestPage(pr);
        when(analysisRunRepository.findByPullRequestIdInOrderByPullRequestIdAscCreatedAtDesc(List.of(42L)))
                .thenReturn(List.of());

        var row = service.findSummaryPage(new com.gatekeeper.pullrequest.dto.PullRequestFilter(null, null),
                PageRequest.of(0, 20)).getContent().get(0);

        assertThat(row.number()).isEqualTo(7);
        assertThat(row.repositoryOwner()).isEqualTo("gatekeeper");
        assertThat(row.repositoryName()).isEqualTo("core");
        assertThat(row.repositoryFullName()).isEqualTo("gatekeeper/core");
        assertThat(row.githubUrl()).isEqualTo("https://github.com/gatekeeper/core/pull/7");
    }

    @Test
    void findSummaryPage_skipsBatchQueriesEntirelyWhenThePageIsEmpty() {
        when(pullRequestRepository.findAll(
                ArgumentMatchers.<Specification<PullRequest>>any(), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        Page<com.gatekeeper.pullrequest.dto.PullRequestSummaryResponse> result =
                service.findSummaryPage(new com.gatekeeper.pullrequest.dto.PullRequestFilter(null, null), PageRequest.of(0, 20));

        assertThat(result.getContent()).isEmpty();
        verify(analysisRunRepository, never()).findByPullRequestIdInOrderByPullRequestIdAscCreatedAtDesc(any());
        verify(verdictRepository, never()).findByAnalysisRunIdIn(any());
    }

    @Test
    void findDetailByIdOrThrow_returnsFullHistoryNewestFirstWithVerdictOutcomesPerRun() {
        PullRequest pr = existingOpenPullRequest("Add login page", "abc123");
        ReflectionTestUtils.setField(pr, "id", 42L);
        when(pullRequestRepository.findWithRepositoryById(42L)).thenReturn(Optional.of(pr));
        AnalysisRun newer = analysisRunFor(pr, 9L, AnalysisRunStatus.COMPLETED);
        AnalysisRun older = analysisRunFor(pr, 8L, AnalysisRunStatus.FAILED);
        when(analysisRunRepository.findByPullRequestIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(newer, older));
        Verdict verdict = Verdict.builder().analysisRun(newer).outcome(VerdictOutcome.APPROVED).build();
        when(verdictRepository.findByAnalysisRunIdIn(List.of(9L, 8L))).thenReturn(List.of(verdict));

        var result = service.findDetailByIdOrThrow(42L);

        assertThat(result.id()).isEqualTo(42L);
        assertThat(result.number()).isEqualTo(7);
        assertThat(result.repository().owner()).isEqualTo("gatekeeper");
        assertThat(result.repository().name()).isEqualTo("core");
        assertThat(result.githubUrl()).isEqualTo("https://github.com/gatekeeper/core/pull/7");
        assertThat(result.analysisRuns()).hasSize(2);
        assertThat(result.analysisRuns().get(0).id()).isEqualTo(9L);
        assertThat(result.analysisRuns().get(0).verdictOutcome()).isEqualTo(VerdictOutcome.APPROVED);
        assertThat(result.analysisRuns().get(1).id()).isEqualTo(8L);
        assertThat(result.analysisRuns().get(1).verdictOutcome()).isNull();
    }

    @Test
    void findDetailByIdOrThrow_returnsAnEmptyHistoryWhenNoAnalysisRunExistsYet() {
        PullRequest pr = existingOpenPullRequest("Add login page", "abc123");
        ReflectionTestUtils.setField(pr, "id", 42L);
        when(pullRequestRepository.findWithRepositoryById(42L)).thenReturn(Optional.of(pr));
        when(analysisRunRepository.findByPullRequestIdOrderByCreatedAtDesc(42L)).thenReturn(List.of());

        var result = service.findDetailByIdOrThrow(42L);

        assertThat(result.analysisRuns()).isEmpty();
        verify(verdictRepository, never()).findByAnalysisRunIdIn(any());
    }

    @Test
    void findDetailByIdOrThrow_throwsResourceNotFoundExceptionWhenMissing() {
        when(pullRequestRepository.findWithRepositoryById(404L)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.findDetailByIdOrThrow(404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void stubPullRequestPage(PullRequest pr) {
        when(pullRequestRepository.findAll(
                ArgumentMatchers.<Specification<PullRequest>>any(), ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pr)));
    }

    private AnalysisRun analysisRunFor(PullRequest pr, Long id, AnalysisRunStatus status) {
        AnalysisRun run = AnalysisRun.builder()
                .pullRequest(pr)
                .commitSha("sha-" + id)
                .status(status)
                .triggerReason(AnalysisRunTriggerReason.OPENED)
                .build();
        ReflectionTestUtils.setField(run, "id", id);
        return run;
    }

    private PullRequest existingOpenPullRequest(String title, String headSha) {
        return PullRequest.builder()
                .repository(repository)
                .githubPrId(GITHUB_PR_ID)
                .number(7)
                .title(title)
                .authorLogin("octocat")
                .sourceBranch("feature")
                .targetBranch("main")
                .headSha(headSha)
                .status(PullRequestStatus.OPEN)
                .build();
    }

    private PullRequestUpsertCommand openedCommand(String title, String headSha) {
        return new PullRequestUpsertCommand(
                GITHUB_PR_ID, 7, title, "octocat", "feature", "main", headSha, "open", false);
    }
}
