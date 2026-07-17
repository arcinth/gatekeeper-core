package com.gatekeeper.github;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.github.dto.InstallationRepositoriesWebhookPayload;
import com.gatekeeper.github.dto.InstallationWebhookPayload;
import com.gatekeeper.github.dto.PullRequestWebhookPayload;
import com.gatekeeper.github.exception.MalformedWebhookPayloadException;
import com.gatekeeper.orchestration.AnalysisOrchestrator;
import com.gatekeeper.repository.RepositoryService;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class GitHubEventRouterTest {

    private static final String DELIVERY_ID = "delivery-1";

    private final AnalysisOrchestrator analysisOrchestrator = mock(AnalysisOrchestrator.class);
    private final GitHubInstallationService gitHubInstallationService = mock(GitHubInstallationService.class);
    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final GitHubEventRouter router = new GitHubEventRouter(
            analysisOrchestrator, gitHubInstallationService, repositoryService, new ObjectMapper());

    @Test
    void route_parsesAndForwardsPullRequestEvents() {
        byte[] payload = """
                {"action":"opened","pull_request":{"id":1,"number":7,"title":"t",
                "user":{"login":"octocat"},"head":{"ref":"feature","sha":"abc"},
                "base":{"ref":"main"},"state":"open","merged":false},
                "repository":{"id":99,"full_name":"org/repo"},
                "installation":{"id":55}}
                """.getBytes(StandardCharsets.UTF_8);

        router.route("pull_request", payload, DELIVERY_ID);

        verify(analysisOrchestrator).handlePullRequestEvent(
                argThatMatchesAction("opened"), eq(DELIVERY_ID));
    }

    @Test
    void route_parsesAndForwardsInstallationEvents() {
        byte[] payload = """
                {"action":"created","installation":{"id":55,
                "account":{"id":9,"login":"octocat","type":"User"},
                "repository_selection":"all","permissions":{"contents":"read"}}}
                """.getBytes(StandardCharsets.UTF_8);

        router.route("installation", payload, DELIVERY_ID);

        verify(gitHubInstallationService).handleInstallationEvent(
                argThatMatchesInstallationAction("created"), eq(DELIVERY_ID));
    }

    @Test
    void route_parsesAndForwardsInstallationRepositoriesEvents() {
        byte[] payload = """
                {"action":"added","installation":{"id":55},
                "repositories_added":[{"id":100,"name":"core","full_name":"octocat/core"}],
                "repositories_removed":[]}
                """.getBytes(StandardCharsets.UTF_8);

        router.route("installation_repositories", payload, DELIVERY_ID);

        verify(repositoryService).handleInstallationRepositoriesEvent(
                argThatMatchesInstallationRepositoriesAction("added"), eq(DELIVERY_ID));
    }

    @Test
    void route_ignoresNonPullRequestInstallationOrInstallationRepositoriesEventTypesWithoutTouchingAnyHandler() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);

        assertThatCode(() -> router.route("issues", payload, DELIVERY_ID)).doesNotThrowAnyException();

        verify(analysisOrchestrator, never()).handlePullRequestEvent(any(), any());
        verify(gitHubInstallationService, never()).handleInstallationEvent(any(), any());
        verify(repositoryService, never()).handleInstallationRepositoriesEvent(any(), any());
    }

    @Test
    void route_throwsMalformedWebhookPayloadExceptionForInvalidInstallationRepositoriesJson() {
        byte[] payload = "not valid json at all".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> router.route("installation_repositories", payload, DELIVERY_ID))
                .isInstanceOf(MalformedWebhookPayloadException.class);

        verify(repositoryService, never()).handleInstallationRepositoriesEvent(any(), any());
    }

    @Test
    void route_throwsMalformedWebhookPayloadExceptionForInvalidInstallationJson() {
        byte[] payload = "not valid json at all".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> router.route("installation", payload, DELIVERY_ID))
                .isInstanceOf(MalformedWebhookPayloadException.class);

        verify(gitHubInstallationService, never()).handleInstallationEvent(any(), any());
    }

    @Test
    void route_ignoresNullEventType() {
        byte[] payload = "{}".getBytes(StandardCharsets.UTF_8);

        assertThatCode(() -> router.route(null, payload, DELIVERY_ID)).doesNotThrowAnyException();

        verify(analysisOrchestrator, never()).handlePullRequestEvent(any(), any());
    }

    @Test
    void route_throwsMalformedWebhookPayloadExceptionForInvalidJson() {
        byte[] payload = "not valid json at all".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> router.route("pull_request", payload, DELIVERY_ID))
                .isInstanceOf(MalformedWebhookPayloadException.class);

        verify(analysisOrchestrator, never()).handlePullRequestEvent(any(), any());
    }

    @Test
    void route_throwsMalformedWebhookPayloadExceptionWhenShapeDoesNotMatch() {
        // Valid JSON, but "pull_request" is a string where an object is expected.
        byte[] payload = "{\"action\":\"opened\",\"pull_request\":\"not-an-object\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> router.route("pull_request", payload, DELIVERY_ID))
                .isInstanceOf(MalformedWebhookPayloadException.class);
    }

    private static PullRequestWebhookPayload argThatMatchesAction(String expectedAction) {
        return org.mockito.ArgumentMatchers.argThat(payload -> payload.action().equals(expectedAction));
    }

    private static InstallationRepositoriesWebhookPayload argThatMatchesInstallationRepositoriesAction(
            String expectedAction) {
        return org.mockito.ArgumentMatchers.argThat(payload -> payload.action().equals(expectedAction));
    }

    private static InstallationWebhookPayload argThatMatchesInstallationAction(String expectedAction) {
        return org.mockito.ArgumentMatchers.argThat(payload -> payload.action().equals(expectedAction));
    }
}
