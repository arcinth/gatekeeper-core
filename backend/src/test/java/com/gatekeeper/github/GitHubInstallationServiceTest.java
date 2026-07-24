package com.gatekeeper.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.github.dto.InstallationWebhookPayload;
import com.gatekeeper.github.dto.InstallationWebhookPayload.AccountData;
import com.gatekeeper.github.dto.InstallationWebhookPayload.InstallationData;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.organization.OrganizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

class GitHubInstallationServiceTest {

    private static final String DELIVERY_ID = "delivery-1";
    private static final long INSTALLATION_ID = 555L;
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String APP_JWT = "test-app-jwt";

    private final GitHubInstallationRepository gitHubInstallationRepository = mock(GitHubInstallationRepository.class);
    private final OrganizationService organizationService = mock(OrganizationService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final GitHubAppAuthService gitHubAppAuthService = mock(GitHubAppAuthService.class);
    private final GitHubApiClient gitHubApiClient = mock(GitHubApiClient.class);
    private final Organization organization = Organization.builder().name("Default Organization").build();
    private final GitHubInstallationService service = new GitHubInstallationService(
            gitHubInstallationRepository, organizationService, new ObjectMapper(), eventPublisher, clock,
            gitHubAppAuthService, gitHubApiClient);

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

    @Test
    void reconcileInstallation_fetchesDirectlyFromGitHubAndUpsertsIt() {
        when(gitHubAppAuthService.mintAppJwt()).thenReturn(APP_JWT);
        InstallationData data = new InstallationData(
                INSTALLATION_ID, new AccountData(9L, "octocat", "User"), "all", Map.of("contents", "read"));
        when(gitHubApiClient.getInstallation(INSTALLATION_ID, APP_JWT)).thenReturn(Optional.of(data));
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());
        when(gitHubInstallationRepository.findAll()).thenReturn(List.of());

        GitHubInstallation result = service.reconcileInstallation(INSTALLATION_ID);

        assertThat(result.getInstallationId()).isEqualTo(INSTALLATION_ID);
        assertThat(result.getGithubAccountLogin()).isEqualTo("octocat");
        assertThat(result.getStatus()).isEqualTo(GitHubInstallationStatus.CONNECTING);
        // Unlike the webhook path, reconcile does not itself trigger a repository
        // sync - the caller (the onboarding callback) triggers that explicitly.
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void reconcileInstallation_throwsResourceNotFoundWhenGitHubNoLongerRecognizesTheInstallation() {
        when(gitHubAppAuthService.mintAppJwt()).thenReturn(APP_JWT);
        when(gitHubApiClient.getInstallation(INSTALLATION_ID, APP_JWT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reconcileInstallation(INSTALLATION_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reconcileInstallation_disconnectsAnotherActiveInstallationGitHubNoLongerRecognizes() {
        long staleInstallationId = 48211907L;
        when(gitHubAppAuthService.mintAppJwt()).thenReturn(APP_JWT);
        InstallationData data = new InstallationData(
                INSTALLATION_ID, new AccountData(9L, "octocat", "User"), "all", Map.of());
        when(gitHubApiClient.getInstallation(INSTALLATION_ID, APP_JWT)).thenReturn(Optional.of(data));
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());

        GitHubInstallation stale = GitHubInstallation.builder()
                .organization(organization)
                .installationId(staleInstallationId)
                .githubAccountLogin("northwind")
                .active(true)
                .status(GitHubInstallationStatus.ACTIVE)
                .build();
        // BaseEntity's equals()/hashCode() is id-based - an explicit id keeps this
        // row distinguishable from the other transient GitHubInstallation this
        // reconcile call also builds (both would otherwise carry a null id, and
        // therefore compare equal to each other).
        ReflectionTestUtils.setField(stale, "id", 20L);
        when(gitHubInstallationRepository.findAll()).thenReturn(List.of(stale));
        when(gitHubApiClient.getInstallation(eq(staleInstallationId), eq(APP_JWT))).thenReturn(Optional.empty());

        service.reconcileInstallation(INSTALLATION_ID);

        assertThat(stale.isActive()).isFalse();
        assertThat(stale.getStatus()).isEqualTo(GitHubInstallationStatus.DISCONNECTED);
    }

    @Test
    void reconcileInstallation_leavesAnotherActiveInstallationAloneWhenGitHubStillRecognizesIt() {
        long otherInstallationId = 999L;
        when(gitHubAppAuthService.mintAppJwt()).thenReturn(APP_JWT);
        InstallationData data = new InstallationData(
                INSTALLATION_ID, new AccountData(9L, "octocat", "User"), "all", Map.of());
        when(gitHubApiClient.getInstallation(INSTALLATION_ID, APP_JWT)).thenReturn(Optional.of(data));
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());

        GitHubInstallation other = GitHubInstallation.builder()
                .organization(organization)
                .installationId(otherInstallationId)
                .githubAccountLogin("another-org")
                .active(true)
                .status(GitHubInstallationStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(other, "id", 21L);
        when(gitHubInstallationRepository.findAll()).thenReturn(List.of(other));
        when(gitHubApiClient.getInstallation(eq(otherInstallationId), eq(APP_JWT)))
                .thenReturn(Optional.of(new InstallationData(
                        otherInstallationId, new AccountData(10L, "another-org", "Organization"), "all", Map.of())));

        service.reconcileInstallation(INSTALLATION_ID);

        assertThat(other.isActive()).isTrue();
        assertThat(other.getStatus()).isEqualTo(GitHubInstallationStatus.ACTIVE);
        verify(gitHubInstallationRepository, never()).save(other);
    }

    private static InstallationWebhookPayload payload(
            String action, String login, String accountType, String repositorySelection, Map<String, String> permissions) {
        return new InstallationWebhookPayload(
                action,
                new InstallationData(INSTALLATION_ID, new AccountData(9L, login, accountType),
                        repositorySelection, permissions));
    }
}
