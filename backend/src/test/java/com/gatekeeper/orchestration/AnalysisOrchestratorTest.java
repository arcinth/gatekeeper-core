package com.gatekeeper.orchestration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.github.GitHubInstallation;
import com.gatekeeper.github.dto.PullRequestWebhookPayload;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.BranchRef;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.InstallationData;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.PullRequestData;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.RepositoryData;
import com.gatekeeper.github.dto.PullRequestWebhookPayload.UserData;
import com.gatekeeper.github.exception.MalformedWebhookPayloadException;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestService;
import com.gatekeeper.pullrequest.PullRequestUpsertCommand;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.repository.RepositoryLookupService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AnalysisOrchestratorTest {

    private static final long GITHUB_REPOSITORY_ID = 99L;
    private static final long LINKED_INSTALLATION_ID = 55L;
    private static final String DELIVERY_ID = "delivery-1";

    private final RepositoryLookupService repositoryLookupService = mock(RepositoryLookupService.class);
    private final PullRequestService pullRequestService = mock(PullRequestService.class);
    private final AnalysisRunService analysisRunService = mock(AnalysisRunService.class);
    private final AnalysisOrchestrator orchestrator =
            new AnalysisOrchestrator(repositoryLookupService, pullRequestService, analysisRunService);

    private Repository linkedRepository;
    private PullRequest persistedPullRequest;

    @BeforeEach
    void setUp() {
        linkedRepository = Repository.builder()
                .fullName("gatekeeper/core")
                .githubRepositoryId(GITHUB_REPOSITORY_ID)
                .githubInstallation(GitHubInstallation.builder().installationId(LINKED_INSTALLATION_ID).build())
                .build();
        persistedPullRequest = PullRequest.builder().number(7).build();

        when(repositoryLookupService.findLinkedRepository(GITHUB_REPOSITORY_ID)).thenReturn(Optional.of(linkedRepository));
        when(pullRequestService.upsert(eq(linkedRepository), any(PullRequestUpsertCommand.class)))
                .thenReturn(persistedPullRequest);
        when(analysisRunService.createIfAbsent(eq(persistedPullRequest), anyString(), any(AnalysisRunTriggerReason.class)))
                .thenReturn(AnalysisRun.builder().pullRequest(persistedPullRequest).build());
    }

    @ParameterizedTest
    @CsvSource({
            "opened, OPENED",
            "reopened, REOPENED",
            "synchronize, SYNCHRONIZE"
    })
    void handlePullRequestEvent_createsAnalysisRunWithMatchingTriggerReason(String action, AnalysisRunTriggerReason expectedReason) {
        orchestrator.handlePullRequestEvent(payloadWithAction(action), DELIVERY_ID);

        verify(analysisRunService).createIfAbsent(persistedPullRequest, "abc123", expectedReason);
    }

    @Test
    void handlePullRequestEvent_upsertsPullRequestButDoesNotCreateAnalysisRunOnClose() {
        orchestrator.handlePullRequestEvent(payloadWithAction("closed"), DELIVERY_ID);

        verify(pullRequestService).upsert(eq(linkedRepository), any(PullRequestUpsertCommand.class));
        verify(analysisRunService, never()).createIfAbsent(any(), anyString(), any());
    }

    @Test
    void handlePullRequestEvent_ignoresActionsGateKeeperDoesNotActOn() {
        orchestrator.handlePullRequestEvent(payloadWithAction("labeled"), DELIVERY_ID);

        verify(pullRequestService, never()).upsert(any(), any());
        verify(analysisRunService, never()).createIfAbsent(any(), anyString(), any());
    }

    @Test
    void handlePullRequestEvent_skipsProcessingWhenRepositoryIsNotLinked() {
        when(repositoryLookupService.findLinkedRepository(GITHUB_REPOSITORY_ID)).thenReturn(Optional.empty());

        orchestrator.handlePullRequestEvent(payloadWithAction("opened"), DELIVERY_ID);

        verify(pullRequestService, never()).upsert(any(), any());
        verify(analysisRunService, never()).createIfAbsent(any(), anyString(), any());
    }

    @Test
    void handlePullRequestEvent_stillProcessesWhenWebhookInstallationDiffersFromLinkedInstallation() {
        PullRequestWebhookPayload payload = new PullRequestWebhookPayload(
                "opened",
                pullRequestData("abc123"),
                new RepositoryData(GITHUB_REPOSITORY_ID, "gatekeeper/core", "main"),
                new InstallationData(999L));

        assertThatCode(() -> orchestrator.handlePullRequestEvent(payload, DELIVERY_ID)).doesNotThrowAnyException();

        verify(analysisRunService).createIfAbsent(persistedPullRequest, "abc123", AnalysisRunTriggerReason.OPENED);
    }

    @Test
    void handlePullRequestEvent_toleratesAMissingInstallationBlockInThePayload() {
        PullRequestWebhookPayload payload = new PullRequestWebhookPayload(
                "opened", pullRequestData("abc123"),
                new RepositoryData(GITHUB_REPOSITORY_ID, "gatekeeper/core", "main"),
                null);

        assertThatCode(() -> orchestrator.handlePullRequestEvent(payload, DELIVERY_ID)).doesNotThrowAnyException();
    }

    @Test
    void handlePullRequestEvent_rejectsPayloadMissingAction() {
        PullRequestWebhookPayload payload = new PullRequestWebhookPayload(
                null, pullRequestData("abc123"),
                new RepositoryData(GITHUB_REPOSITORY_ID, "gatekeeper/core", "main"),
                new InstallationData(LINKED_INSTALLATION_ID));

        assertThatThrownBy(() -> orchestrator.handlePullRequestEvent(payload, DELIVERY_ID))
                .isInstanceOf(MalformedWebhookPayloadException.class);
    }

    @Test
    void handlePullRequestEvent_rejectsPayloadMissingRepository() {
        PullRequestWebhookPayload payload = new PullRequestWebhookPayload(
                "opened", pullRequestData("abc123"), null, new InstallationData(LINKED_INSTALLATION_ID));

        assertThatThrownBy(() -> orchestrator.handlePullRequestEvent(payload, DELIVERY_ID))
                .isInstanceOf(MalformedWebhookPayloadException.class);
    }

    @Test
    void handlePullRequestEvent_rejectsPayloadMissingPullRequestDetails() {
        PullRequestWebhookPayload payload = new PullRequestWebhookPayload(
                "opened", null,
                new RepositoryData(GITHUB_REPOSITORY_ID, "gatekeeper/core", "main"),
                new InstallationData(LINKED_INSTALLATION_ID));

        assertThatThrownBy(() -> orchestrator.handlePullRequestEvent(payload, DELIVERY_ID))
                .isInstanceOf(MalformedWebhookPayloadException.class);
    }

    @Test
    void handlePullRequestEvent_rejectsPayloadMissingHeadBranchDetails() {
        PullRequestData incomplete = new PullRequestData(
                1L, 7, "title", new UserData("octocat"), null, new BranchRef("main", "def"), "open", false);
        PullRequestWebhookPayload payload = new PullRequestWebhookPayload(
                "opened", incomplete,
                new RepositoryData(GITHUB_REPOSITORY_ID, "gatekeeper/core", "main"),
                new InstallationData(LINKED_INSTALLATION_ID));

        assertThatThrownBy(() -> orchestrator.handlePullRequestEvent(payload, DELIVERY_ID))
                .isInstanceOf(MalformedWebhookPayloadException.class);
    }

    private PullRequestWebhookPayload payloadWithAction(String action) {
        return new PullRequestWebhookPayload(
                action,
                pullRequestData("abc123"),
                new RepositoryData(GITHUB_REPOSITORY_ID, "gatekeeper/core", "main"),
                new InstallationData(LINKED_INSTALLATION_ID));
    }

    private PullRequestData pullRequestData(String headSha) {
        return new PullRequestData(
                1L, 7, "Add login page", new UserData("octocat"),
                new BranchRef("feature", headSha), new BranchRef("main", "def456"),
                "open", false);
    }
}
