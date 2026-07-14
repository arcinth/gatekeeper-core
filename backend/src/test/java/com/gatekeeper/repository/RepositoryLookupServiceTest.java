package com.gatekeeper.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gatekeeper.github.GitHubInstallation;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RepositoryLookupServiceTest {

    private static final long GITHUB_REPOSITORY_ID = 987654321L;

    private final RepositoryRepository repositoryRepository = mock(RepositoryRepository.class);
    private final RepositoryLookupService service = new RepositoryLookupService(repositoryRepository);

    @Test
    void findLinkedRepository_returnsRepositoryWhenLinkedToAnInstallation() {
        Repository repository = Repository.builder()
                .fullName("gatekeeper/core")
                .githubRepositoryId(GITHUB_REPOSITORY_ID)
                .githubInstallation(GitHubInstallation.builder().installationId(1L).build())
                .build();
        when(repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID)).thenReturn(Optional.of(repository));

        Optional<Repository> result = service.findLinkedRepository(GITHUB_REPOSITORY_ID);

        assertThat(result).contains(repository);
    }

    @Test
    void findLinkedRepository_returnsEmptyWhenNoRepositoryIsRegistered() {
        when(repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID)).thenReturn(Optional.empty());

        Optional<Repository> result = service.findLinkedRepository(GITHUB_REPOSITORY_ID);

        assertThat(result).isEmpty();
    }

    @Test
    void findLinkedRepository_returnsEmptyWhenRepositoryHasNoLinkedInstallation() {
        Repository repository = Repository.builder()
                .fullName("gatekeeper/core")
                .githubRepositoryId(GITHUB_REPOSITORY_ID)
                .githubInstallation(null)
                .build();
        when(repositoryRepository.findByGithubRepositoryId(GITHUB_REPOSITORY_ID)).thenReturn(Optional.of(repository));

        Optional<Repository> result = service.findLinkedRepository(GITHUB_REPOSITORY_ID);

        assertThat(result).isEmpty();
    }
}
