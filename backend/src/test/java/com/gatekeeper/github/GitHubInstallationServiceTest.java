package com.gatekeeper.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.github.dto.InstallationWebhookPayload;
import com.gatekeeper.github.dto.InstallationWebhookPayload.AccountData;
import com.gatekeeper.github.dto.InstallationWebhookPayload.InstallationData;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.organization.OrganizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class GitHubInstallationServiceTest {

    private static final String DELIVERY_ID = "delivery-1";
    private static final long INSTALLATION_ID = 555L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    private final GitHubInstallationRepository gitHubInstallationRepository = mock(GitHubInstallationRepository.class);
    private final OrganizationService organizationService = mock(OrganizationService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final Organization organization = Organization.builder().name("Default Organization").build();
    private final GitHubInstallationService service = new GitHubInstallationService(
            gitHubInstallationRepository, organizationService, new ObjectMapper(), eventPublisher, clock);

    @BeforeEach
    void setUp() {
        when(organizationService.getDefaultOrganization()).thenReturn(organization);
        when(gitHubInstallationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void handleInstallationEvent_createsANewInstallationWhenNoneExistsForThisInstallationId() {
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());

        service.handleInstallationEvent(payload("created", "octocat", "User", "all",
                Map.of("contents", "read")), DELIVERY_ID);

        ArgumentCaptor<GitHubInstallation> saved = ArgumentCaptor.forClass(GitHubInstallation.class);
        verify(gitHubInstallationRepository).save(saved.capture());
        GitHubInstallation installation = saved.getValue();
        assertThat(installation.getOrganization()).isEqualTo(organization);
        assertThat(installation.getInstallationId()).isEqualTo(INSTALLATION_ID);
        assertThat(installation.getGithubAccountLogin()).isEqualTo("octocat");
        assertThat(installation.getGithubAccountId()).isEqualTo(9L);
        assertThat(installation.getGithubAccountType()).isEqualTo("User");
        assertThat(installation.getRepositorySelection()).isEqualTo("all");
        assertThat(installation.getPermissions()).isEqualTo("{\"contents\":\"read\"}");
        assertThat(installation.isActive()).isTrue();
        assertThat(installation.getStatus()).isEqualTo(GitHubInstallationStatus.CONNECTING);

        ArgumentCaptor<InstallationRepositorySyncRequestedEvent> published =
                ArgumentCaptor.forClass(InstallationRepositorySyncRequestedEvent.class);
        verify(eventPublisher).publishEvent(published.capture());
        assertThat(published.getValue().installationId()).isEqualTo(INSTALLATION_ID);
    }

    @Test
    void handleInstallationEvent_updatesTheExistingRowInsteadOfCreatingADuplicate() {
        GitHubInstallation existing = GitHubInstallation.builder()
                .organization(organization)
                .installationId(INSTALLATION_ID)
                .githubAccountLogin("old-login")
                .repositorySelection("selected")
                .active(false)
                .status(GitHubInstallationStatus.ACTIVE)
                .build();
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(existing));

        service.handleInstallationEvent(payload("new_permissions_accepted", "octocat", "Organization", "all",
                Map.of("pull_requests", "write")), DELIVERY_ID);

        ArgumentCaptor<GitHubInstallation> saved = ArgumentCaptor.forClass(GitHubInstallation.class);
        verify(gitHubInstallationRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(existing.getGithubAccountLogin()).isEqualTo("octocat");
        assertThat(existing.getGithubAccountType()).isEqualTo("Organization");
        assertThat(existing.getRepositorySelection()).isEqualTo("all");
        assertThat(existing.isActive()).isTrue();
        // Already mid-lifecycle (ACTIVE, not DISCONNECTED) - a routine permissions-accepted
        // update must not reset an already-synced installation back to CONNECTING.
        assertThat(existing.getStatus()).isEqualTo(GitHubInstallationStatus.ACTIVE);
    }

    @Test
    void handleInstallationEvent_resetsStatusToConnectingWhenReconnectingAPreviouslyDisconnectedInstallation() {
        GitHubInstallation existing = GitHubInstallation.builder()
                .organization(organization)
                .installationId(INSTALLATION_ID)
                .githubAccountLogin("octocat")
                .active(false)
                .status(GitHubInstallationStatus.DISCONNECTED)
                .build();
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(existing));

        service.handleInstallationEvent(payload("created", "octocat", "User", "all", Map.of()), DELIVERY_ID);

        assertThat(existing.isActive()).isTrue();
        assertThat(existing.getStatus()).isEqualTo(GitHubInstallationStatus.CONNECTING);
    }

    @Test
    void handleInstallationEvent_marksAnExistingInstallationInactiveOnDeletion() {
        GitHubInstallation existing = GitHubInstallation.builder()
                .organization(organization)
                .installationId(INSTALLATION_ID)
                .githubAccountLogin("octocat")
                .active(true)
                .status(GitHubInstallationStatus.ACTIVE)
                .build();
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(existing));

        service.handleInstallationEvent(payload("deleted", "octocat", "User", "all", Map.of()), DELIVERY_ID);

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getStatus()).isEqualTo(GitHubInstallationStatus.DISCONNECTED);
        verify(gitHubInstallationRepository).save(existing);
        verify(organizationService, never()).getDefaultOrganization();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void handleInstallationEvent_ignoresDeletionForAnInstallationItNeverKnewAbout() {
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());

        service.handleInstallationEvent(payload("deleted", "octocat", "User", "all", Map.of()), DELIVERY_ID);

        verify(gitHubInstallationRepository, never()).save(any());
    }

    @Test
    void markSyncing_setsStatusToSyncing() {
        GitHubInstallation installation = GitHubInstallation.builder()
                .installationId(INSTALLATION_ID).status(GitHubInstallationStatus.CONNECTING).build();
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(installation));

        service.markSyncing(INSTALLATION_ID);

        assertThat(installation.getStatus()).isEqualTo(GitHubInstallationStatus.SYNCING);
    }

    @Test
    void markSynced_setsStatusActiveStampsSuccessTimeAndClearsAnyPriorError() {
        GitHubInstallation installation = GitHubInstallation.builder()
                .installationId(INSTALLATION_ID)
                .status(GitHubInstallationStatus.SYNCING)
                .lastSyncError("previous failure")
                .build();
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(installation));

        service.markSynced(INSTALLATION_ID);

        assertThat(installation.getStatus()).isEqualTo(GitHubInstallationStatus.ACTIVE);
        assertThat(installation.getLastSuccessfulSyncAt()).isEqualTo(NOW);
        assertThat(installation.getLastSyncError()).isNull();
    }

    @Test
    void markSyncFailed_setsStatusErrorAndRecordsTheErrorMessage() {
        GitHubInstallation installation = GitHubInstallation.builder()
                .installationId(INSTALLATION_ID).status(GitHubInstallationStatus.SYNCING).build();
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(installation));

        service.markSyncFailed(INSTALLATION_ID, "GitHub API returned 503");

        assertThat(installation.getStatus()).isEqualTo(GitHubInstallationStatus.ERROR);
        assertThat(installation.getLastSyncError()).isEqualTo("GitHub API returned 503");
    }

    @Test
    void markSyncFailed_truncatesAnOverlyLongErrorMessage() {
        GitHubInstallation installation = GitHubInstallation.builder().installationId(INSTALLATION_ID).build();
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(installation));
        String longMessage = "x".repeat(2000);

        service.markSyncFailed(INSTALLATION_ID, longMessage);

        assertThat(installation.getLastSyncError()).hasSize(1000);
    }

    @Test
    void markSyncing_isANoOpWhenTheInstallationNoLongerExists() {
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());

        service.markSyncing(INSTALLATION_ID);

        verify(gitHubInstallationRepository, never()).save(any());
    }

    private static InstallationWebhookPayload payload(
            String action, String login, String accountType, String repositorySelection, Map<String, String> permissions) {
        return new InstallationWebhookPayload(
                action,
                new InstallationData(INSTALLATION_ID, new AccountData(9L, login, accountType),
                        repositorySelection, permissions));
    }
}
