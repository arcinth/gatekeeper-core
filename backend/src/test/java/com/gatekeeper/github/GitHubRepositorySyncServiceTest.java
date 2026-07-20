package com.gatekeeper.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.github.dto.InstallationRepositoriesResponse.RepositorySummary;
import com.gatekeeper.repository.RepositoryService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class GitHubRepositorySyncServiceTest {

    private static final long INSTALLATION_ID = 147259549L;

    private final GitHubInstallationRepository gitHubInstallationRepository = mock(GitHubInstallationRepository.class);
    private final GitHubAppAuthService gitHubAppAuthService = mock(GitHubAppAuthService.class);
    private final GitHubApiClient gitHubApiClient = mock(GitHubApiClient.class);
    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final GitHubInstallationService gitHubInstallationService = mock(GitHubInstallationService.class);
    private final GitHubRepositorySyncService service = new GitHubRepositorySyncService(
            gitHubInstallationRepository, gitHubAppAuthService, gitHubApiClient, repositoryService,
            gitHubInstallationService);

    @Test
    void synchronize_fetchesRepositoriesAndDelegatesToRepositoryService() {
        GitHubInstallation installation = GitHubInstallation.builder().installationId(INSTALLATION_ID).build();
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(installation));
        when(gitHubAppAuthService.getInstallationAccessToken(INSTALLATION_ID)).thenReturn("installation-token");
        List<RepositorySummary> repositories = List.of(
                new RepositorySummary(1299531781L, "gatekeeper-core", "arcinth/gatekeeper-core"));
        when(gitHubApiClient.listInstallationRepositories("installation-token")).thenReturn(repositories);

        int count = service.synchronize(INSTALLATION_ID);

        ArgumentCaptor<List<RepositorySummary>> passed = ArgumentCaptor.forClass(List.class);
        verify(repositoryService).synchronizeFromInstallation(eq(INSTALLATION_ID), passed.capture());
        assertThat(passed.getValue()).containsExactly(
                new RepositorySummary(1299531781L, "gatekeeper-core", "arcinth/gatekeeper-core"));
        assertThat(count).isEqualTo(1);
    }

    @Test
    void synchronize_marksTheInstallationSyncingBeforeCallingGitHubAndSyncedAfterSucceeding() {
        GitHubInstallation installation = GitHubInstallation.builder().installationId(INSTALLATION_ID).build();
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(installation));
        when(gitHubAppAuthService.getInstallationAccessToken(INSTALLATION_ID)).thenReturn("installation-token");
        when(gitHubApiClient.listInstallationRepositories("installation-token")).thenReturn(List.of());

        service.synchronize(INSTALLATION_ID);

        verify(gitHubInstallationService).markSyncing(INSTALLATION_ID);
        verify(gitHubInstallationService).markSynced(INSTALLATION_ID);
        verify(gitHubInstallationService, never()).markSyncFailed(anyLong(), any());
    }

    @Test
    void synchronize_doesNothingForAnInstallationThatNoLongerExists() {
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.empty());

        int count = service.synchronize(INSTALLATION_ID);

        verify(gitHubAppAuthService, never()).getInstallationAccessToken(anyLong());
        verify(repositoryService, never()).synchronizeFromInstallation(anyLong(), any());
        verify(gitHubInstallationService, never()).markSyncing(anyLong());
        assertThat(count).isZero();
    }

    @Test
    void synchronize_swallowsAGitHubApiFailureAndMarksTheInstallationErrorRatherThanPropagating() {
        GitHubInstallation installation = GitHubInstallation.builder().installationId(INSTALLATION_ID).build();
        when(gitHubInstallationRepository.findByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(installation));
        when(gitHubAppAuthService.getInstallationAccessToken(INSTALLATION_ID))
                .thenThrow(new RuntimeException("GitHub is down"));

        int count = service.synchronize(INSTALLATION_ID);

        verify(repositoryService, never()).synchronizeFromInstallation(anyLong(), any());
        verify(gitHubInstallationService).markSyncing(INSTALLATION_ID);
        verify(gitHubInstallationService).markSyncFailed(INSTALLATION_ID, "GitHub is down");
        verify(gitHubInstallationService, never()).markSynced(anyLong());
        assertThat(count).isZero();
    }
}
