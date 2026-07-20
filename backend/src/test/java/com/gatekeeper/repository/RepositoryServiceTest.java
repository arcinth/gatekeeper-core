package com.gatekeeper.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.github.GitHubInstallation;
import com.gatekeeper.github.GitHubInstallationRepository;
import com.gatekeeper.github.dto.InstallationRepositoriesResponse.RepositorySummary;
import com.gatekeeper.github.dto.InstallationRepositoriesWebhookPayload;
import com.gatekeeper.github.dto.InstallationRepositoriesWebhookPayload.InstallationReference;
import com.gatekeeper.github.dto.InstallationRepositoriesWebhookPayload.RepositoryReference;
import com.gatekeeper.auditlog.AuditEvent;
import com.gatekeeper.auditlog.AuditEventType;
import com.gatekeeper.auditlog.AuditLogService;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.organization.OrganizationService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Covers handleInstallationRepositoriesEvent/synchronizeFromInstallation -
 * the only path a repository is ever created or connected since Milestone 8
 * removed manual creation - including the REPOSITORY_CONNECTED/UPDATED audit
 * trail they now produce. update/delete/findAll/findById predate this class
 * and are exercised through RepositoryControllerTest instead.
 */
class RepositoryServiceTest {

    private static final String DELIVERY_ID = "delivery-1";
    private static final long INSTALLATION_ID = 555L;
    private static final long GITHUB_REPOSITORY_ID = 100L;

    private final RepositoryRepository repositoryRepository = mock(RepositoryRepository.class);
    private final GitHubInstallationRepository gitHubInstallationRepository = mock(GitHubInstallationRepository.class);
    private final OrganizationService organizationService = mock(OrganizationService.class);
    private final Organization organization = Organization.builder().name("Default Organization").build();
    private final GitHubInstallation installation =
            GitHubInstallation.builder().organization(organization).installationId(INSTALLATION_ID).build();
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final RepositoryService service =
            new RepositoryService(repositoryRepository, gitHubInstallationRepository, organizationService, auditLogService);

    @BeforeEach
    void setUp() {
        when(organizationService.getDefaultOrganization()).thenReturn(organization);
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(installation));
        when(repositoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void handleInstallationRepositoriesEvent_createsANewRepositoryForANewlyAddedGithubRepository() {
        when(repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID)).thenReturn(Optional.empty());

        service.handleInstallationRepositoriesEvent(
                payload("added", List.of(new RepositoryReference(GITHUB_REPOSITORY_ID, "core", "octocat/core")), List.of()),
                DELIVERY_ID);

        ArgumentCaptor<Repository> saved = ArgumentCaptor.forClass(Repository.class);
        verify(repositoryRepository).save(saved.capture());
        Repository repository = saved.getValue();
        assertThat(repository.getOrganization()).isEqualTo(organization);
        assertThat(repository.getGithubRepositoryId()).isEqualTo(GITHUB_REPOSITORY_ID);
        assertThat(repository.getName()).isEqualTo("core");
        assertThat(repository.getFullName()).isEqualTo("octocat/core");
        assertThat(repository.getOwner()).isEqualTo("octocat");
        assertThat(repository.getGithubInstallation()).isEqualTo(installation);
        assertThat(repository.isActive()).isTrue();

        ArgumentCaptor<AuditEvent> audited = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(audited.capture());
        assertThat(audited.getValue().getEventType()).isEqualTo(AuditEventType.REPOSITORY_CONNECTED);
    }

    @Test
    void handleInstallationRepositoriesEvent_reactivatesAndUpdatesAnExistingRepositoryInsteadOfDuplicating() {
        Repository existing = Repository.builder()
                .organization(organization)
                .githubRepositoryId(GITHUB_REPOSITORY_ID)
                .name("old-name")
                .fullName("octocat/old-name")
                .active(false)
                .build();
        when(repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID)).thenReturn(Optional.of(existing));

        service.handleInstallationRepositoriesEvent(
                payload("added", List.of(new RepositoryReference(GITHUB_REPOSITORY_ID, "core", "octocat/core")), List.of()),
                DELIVERY_ID);

        ArgumentCaptor<Repository> saved = ArgumentCaptor.forClass(Repository.class);
        verify(repositoryRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(existing.getName()).isEqualTo("core");
        assertThat(existing.getFullName()).isEqualTo("octocat/core");
        assertThat(existing.getOwner()).isEqualTo("octocat");
        assertThat(existing.isActive()).isTrue();

        ArgumentCaptor<AuditEvent> audited = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(audited.capture());
        assertThat(audited.getValue().getEventType()).isEqualTo(AuditEventType.REPOSITORY_UPDATED);
    }

    @Test
    void handleInstallationRepositoriesEvent_doesNotAuditARoutineResyncThatChangesNothing() {
        Repository existing = Repository.builder()
                .organization(organization)
                .githubRepositoryId(GITHUB_REPOSITORY_ID)
                .name("core")
                .fullName("octocat/core")
                .active(true)
                .build();
        when(repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID)).thenReturn(Optional.of(existing));

        service.handleInstallationRepositoriesEvent(
                payload("added", List.of(new RepositoryReference(GITHUB_REPOSITORY_ID, "core", "octocat/core")), List.of()),
                DELIVERY_ID);

        verify(auditLogService, never()).record(any());
    }

    @Test
    void handleInstallationRepositoriesEvent_marksAnExistingRepositoryInactiveWhenRemoved() {
        Repository existing = Repository.builder()
                .organization(organization)
                .githubRepositoryId(GITHUB_REPOSITORY_ID)
                .fullName("octocat/core")
                .active(true)
                .build();
        when(repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID)).thenReturn(Optional.of(existing));

        service.handleInstallationRepositoriesEvent(
                payload("removed", List.of(), List.of(new RepositoryReference(GITHUB_REPOSITORY_ID, "core", "octocat/core"))),
                DELIVERY_ID);

        assertThat(existing.isActive()).isFalse();
        verify(repositoryRepository).save(existing);

        ArgumentCaptor<AuditEvent> audited = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(audited.capture());
        assertThat(audited.getValue().getEventType()).isEqualTo(AuditEventType.REPOSITORY_UPDATED);
    }

    @Test
    void handleInstallationRepositoriesEvent_doesNotAuditRemovalOfAnAlreadyInactiveRepository() {
        Repository existing = Repository.builder()
                .organization(organization)
                .githubRepositoryId(GITHUB_REPOSITORY_ID)
                .fullName("octocat/core")
                .active(false)
                .build();
        when(repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID)).thenReturn(Optional.of(existing));

        service.handleInstallationRepositoriesEvent(
                payload("removed", List.of(), List.of(new RepositoryReference(GITHUB_REPOSITORY_ID, "core", "octocat/core"))),
                DELIVERY_ID);

        verify(repositoryRepository, never()).save(any());
        verify(auditLogService, never()).record(any());
    }

    @Test
    void handleInstallationRepositoriesEvent_ignoresRemovalOfARepositoryItNeverKnewAbout() {
        when(repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID)).thenReturn(Optional.empty());

        service.handleInstallationRepositoriesEvent(
                payload("removed", List.of(), List.of(new RepositoryReference(GITHUB_REPOSITORY_ID, "core", "octocat/core"))),
                DELIVERY_ID);

        verify(repositoryRepository, never()).save(any());
    }

    @Test
    void handleInstallationRepositoriesEvent_ignoresTheWholeEventForAnUnknownInstallation() {
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());

        service.handleInstallationRepositoriesEvent(
                payload("added", List.of(new RepositoryReference(GITHUB_REPOSITORY_ID, "core", "octocat/core")), List.of()),
                DELIVERY_ID);

        verify(repositoryRepository, never()).save(any());
    }

    @Test
    void synchronizeFromInstallation_createsARepositoryForEachEntryGitHubReports() {
        when(repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID)).thenReturn(Optional.empty());

        service.synchronizeFromInstallation(INSTALLATION_ID,
                List.of(new RepositorySummary(GITHUB_REPOSITORY_ID, "gatekeeper-core", "arcinth/gatekeeper-core")));

        ArgumentCaptor<Repository> saved = ArgumentCaptor.forClass(Repository.class);
        verify(repositoryRepository).save(saved.capture());
        Repository repository = saved.getValue();
        assertThat(repository.getGithubRepositoryId()).isEqualTo(GITHUB_REPOSITORY_ID);
        assertThat(repository.getName()).isEqualTo("gatekeeper-core");
        assertThat(repository.getFullName()).isEqualTo("arcinth/gatekeeper-core");
        assertThat(repository.getOwner()).isEqualTo("arcinth");
        assertThat(repository.getGithubInstallation()).isEqualTo(installation);
        assertThat(repository.isActive()).isTrue();

        ArgumentCaptor<AuditEvent> audited = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(audited.capture());
        assertThat(audited.getValue().getEventType()).isEqualTo(AuditEventType.REPOSITORY_CONNECTED);
    }

    @Test
    void synchronizeFromInstallation_reactivatesAnExistingRepositoryRatherThanDuplicatingIt() {
        Repository existing = Repository.builder()
                .organization(organization)
                .githubRepositoryId(GITHUB_REPOSITORY_ID)
                .fullName("arcinth/gatekeeper-core")
                .active(false)
                .build();
        when(repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID)).thenReturn(Optional.of(existing));

        service.synchronizeFromInstallation(INSTALLATION_ID,
                List.of(new RepositorySummary(GITHUB_REPOSITORY_ID, "gatekeeper-core", "arcinth/gatekeeper-core")));

        ArgumentCaptor<Repository> saved = ArgumentCaptor.forClass(Repository.class);
        verify(repositoryRepository).save(saved.capture());
        assertThat(saved.getValue()).isSameAs(existing);
        assertThat(existing.isActive()).isTrue();

        ArgumentCaptor<AuditEvent> audited = ArgumentCaptor.forClass(AuditEvent.class);
        verify(auditLogService).record(audited.capture());
        assertThat(audited.getValue().getEventType()).isEqualTo(AuditEventType.REPOSITORY_UPDATED);
    }

    @Test
    void synchronizeFromInstallation_doesNothingForAnUnknownInstallation() {
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());

        service.synchronizeFromInstallation(INSTALLATION_ID,
                List.of(new RepositorySummary(GITHUB_REPOSITORY_ID, "gatekeeper-core", "arcinth/gatekeeper-core")));

        verify(repositoryRepository, never()).save(any());
    }

    private static InstallationRepositoriesWebhookPayload payload(
            String action, List<RepositoryReference> added, List<RepositoryReference> removed) {
        return new InstallationRepositoriesWebhookPayload(
                action, new InstallationReference(INSTALLATION_ID), added, removed);
    }
}
