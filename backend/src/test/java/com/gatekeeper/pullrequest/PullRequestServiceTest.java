package com.gatekeeper.pullrequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.repository.Repository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class PullRequestServiceTest {

    private static final Long GITHUB_PR_ID = 555L;

    private final PullRequestRepository pullRequestRepository = mock(PullRequestRepository.class);
    private final PullRequestService service = new PullRequestService(pullRequestRepository);
    private final Repository repository = Repository.builder().fullName("gatekeeper/core").build();

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
