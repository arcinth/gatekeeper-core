package com.gatekeeper.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class GitHubHealthIndicatorTest {

    private final GitHubInstallationRepository gitHubInstallationRepository = mock(GitHubInstallationRepository.class);

    @Test
    void health_reportsUnknownWhenTheGitHubAppIsNotConfigured() {
        GitHubHealthIndicator indicator = new GitHubHealthIndicator(0L, gitHubInstallationRepository);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsKey("reason");
    }

    @Test
    void health_reportsUpWithInstallationCountsWhenConfigured_neverMakingALiveGitHubApiCall() {
        GitHubInstallation active = GitHubInstallation.builder().status(GitHubInstallationStatus.ACTIVE).build();
        GitHubInstallation error = GitHubInstallation.builder().status(GitHubInstallationStatus.ERROR).build();
        when(gitHubInstallationRepository.findAll()).thenReturn(List.of(active, error));
        GitHubHealthIndicator indicator = new GitHubHealthIndicator(12345L, gitHubInstallationRepository);

        Health health = indicator.health();

        // UP even with an installation in ERROR - a sync failure is actionable detail,
        // not a reason to fail GateKeeper's own readiness/liveness (see class Javadoc).
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("totalInstallations", 2);
        assertThat(health.getDetails()).containsEntry("activeInstallations", 1L);
        assertThat(health.getDetails()).containsEntry("errorInstallations", 1L);
    }

    @Test
    void health_reportsUpWithZeroCountsWhenNoInstallationsExistYet() {
        when(gitHubInstallationRepository.findAll()).thenReturn(List.of());
        GitHubHealthIndicator indicator = new GitHubHealthIndicator(12345L, gitHubInstallationRepository);

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("totalInstallations", 0);
    }
}
