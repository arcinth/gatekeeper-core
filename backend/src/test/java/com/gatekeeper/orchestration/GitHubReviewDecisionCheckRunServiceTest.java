package com.gatekeeper.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.github.GitHubAppAuthService;
import com.gatekeeper.github.GitHubApiClient;
import com.gatekeeper.github.GitHubInstallation;
import com.gatekeeper.github.dto.CheckRunResponse;
import com.gatekeeper.github.dto.CreateCheckRunRequest;
import com.gatekeeper.github.dto.UpdateCheckRunRequest;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.reviewdecision.ReviewDecision;
import com.gatekeeper.reviewdecision.ReviewDecisionRepository;
import com.gatekeeper.reviewdecision.ReviewDecisionType;
import com.gatekeeper.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class GitHubReviewDecisionCheckRunServiceTest {

    private static final String CHECK_RUN_NAME = "GateKeeper Review";
    private static final long INSTALLATION_ID = 881L;
    private static final Instant NOW = Instant.parse("2026-07-20T10:00:00Z");

    private final AnalysisRunService analysisRunService = mock(AnalysisRunService.class);
    private final AnalysisRunRepository analysisRunRepository = mock(AnalysisRunRepository.class);
    private final ReviewDecisionRepository reviewDecisionRepository = mock(ReviewDecisionRepository.class);
    private final ReviewDecisionConclusionMapper conclusionMapper = new ReviewDecisionConclusionMapper();
    private final GitHubAppAuthService gitHubAppAuthService = mock(GitHubAppAuthService.class);
    private final GitHubApiClient gitHubApiClient = mock(GitHubApiClient.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private final GitHubReviewDecisionCheckRunService service = new GitHubReviewDecisionCheckRunService(
            CHECK_RUN_NAME, analysisRunService, analysisRunRepository, reviewDecisionRepository, conclusionMapper,
            gitHubAppAuthService, gitHubApiClient, clock);

    private GitHubInstallation installation;
    private Repository repository;
    private PullRequest pullRequest;
    private AnalysisRun analysisRun;
    private User reviewer;

    @BeforeEach
    void seedFixtures() {
        installation = GitHubInstallation.builder().installationId(INSTALLATION_ID).githubAccountLogin("octocat").build();
        repository = Repository.builder()
                .name("core").owner("gatekeeper").fullName("gatekeeper/core").githubInstallation(installation).build();
        pullRequest = PullRequest.builder().repository(repository).build();
        analysisRun = AnalysisRun.builder().pullRequest(pullRequest).commitSha("sha-review-cr").build();
        reviewer = User.builder().fullName("Ada Reviewer").email("ada@example.com").build();

        when(analysisRunService.findWithPullRequestAndRepositoryByIdOrThrow(1L)).thenReturn(analysisRun);
        when(gitHubAppAuthService.getInstallationAccessToken(INSTALLATION_ID)).thenReturn("ghs_token");
    }

    @Test
    void publishForReviewDecision_createsACheckRunWhenNoneExistsYet() {
        ReviewDecision decision = ReviewDecision.builder()
                .analysisRun(analysisRun).reviewer(reviewer)
                .decision(ReviewDecisionType.APPROVED).comment("Looks good").createdAt(NOW).build();
        when(reviewDecisionRepository.findFirstByAnalysisRunIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(decision));
        when(gitHubApiClient.createCheckRun(eq("gatekeeper/core"), any(CreateCheckRunRequest.class), eq("ghs_token")))
                .thenReturn(new CheckRunResponse(555L));

        service.publishForReviewDecision(1L);

        ArgumentCaptor<CreateCheckRunRequest> captor = ArgumentCaptor.forClass(CreateCheckRunRequest.class);
        verify(gitHubApiClient).createCheckRun(eq("gatekeeper/core"), captor.capture(), eq("ghs_token"));
        CreateCheckRunRequest request = captor.getValue();
        assertThat(request.name()).isEqualTo(CHECK_RUN_NAME);
        assertThat(request.headSha()).isEqualTo("sha-review-cr");
        assertThat(request.status()).isEqualTo("completed");
        assertThat(request.conclusion()).isEqualTo("success");
        assertThat(request.output().title()).contains("APPROVED").contains("Ada Reviewer");
        assertThat(request.output().summary())
                .contains("Ada Reviewer").contains("APPROVED").contains("Looks good").contains(NOW.toString());

        assertThat(analysisRun.getGithubReviewCheckRunId()).isEqualTo(555L);
        verify(analysisRunRepository).save(analysisRun);
        verify(gitHubApiClient, never()).updateCheckRun(any(), anyLong(), any(), any());
    }

    @Test
    void publishForReviewDecision_updatesTheExistingCheckRunRatherThanCreatingANewOne() {
        ReflectionTestUtils.setField(analysisRun, "githubReviewCheckRunId", 555L);
        ReviewDecision decision = ReviewDecision.builder()
                .analysisRun(analysisRun).reviewer(reviewer)
                .decision(ReviewDecisionType.REJECTED).createdAt(NOW).build();
        when(reviewDecisionRepository.findFirstByAnalysisRunIdOrderByCreatedAtDesc(1L))
                .thenReturn(Optional.of(decision));

        service.publishForReviewDecision(1L);

        ArgumentCaptor<UpdateCheckRunRequest> captor = ArgumentCaptor.forClass(UpdateCheckRunRequest.class);
        verify(gitHubApiClient).updateCheckRun(eq("gatekeeper/core"), eq(555L), captor.capture(), eq("ghs_token"));
        assertThat(captor.getValue().conclusion()).isEqualTo("failure");
        assertThat(captor.getValue().output().summary()).doesNotContain("**Comment:**");
        verify(gitHubApiClient, never()).createCheckRun(any(), any(), any());
        verify(analysisRunRepository, never()).save(any());
    }

    @Test
    void publishForReviewDecision_skipsSilentlyWhenTheRepositoryHasNoLinkedInstallation() {
        Repository unlinkedRepository = Repository.builder().name("core").owner("gatekeeper").fullName("gatekeeper/core").build();
        PullRequest unlinkedPullRequest = PullRequest.builder().repository(unlinkedRepository).build();
        AnalysisRun unlinkedRun = AnalysisRun.builder().pullRequest(unlinkedPullRequest).commitSha("sha-x").build();
        when(analysisRunService.findWithPullRequestAndRepositoryByIdOrThrow(2L)).thenReturn(unlinkedRun);

        service.publishForReviewDecision(2L);

        verify(reviewDecisionRepository, never()).findFirstByAnalysisRunIdOrderByCreatedAtDesc(any());
        verify(gitHubApiClient, never()).createCheckRun(any(), any(), any());
        verify(gitHubApiClient, never()).updateCheckRun(any(), anyLong(), any(), any());
    }

    @Test
    void publishForReviewDecision_skipsSilentlyWhenNoReviewDecisionExistsYet() {
        when(reviewDecisionRepository.findFirstByAnalysisRunIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.empty());

        service.publishForReviewDecision(1L);

        verify(gitHubApiClient, never()).createCheckRun(any(), any(), any());
        verify(gitHubApiClient, never()).updateCheckRun(any(), anyLong(), any(), any());
    }
}
