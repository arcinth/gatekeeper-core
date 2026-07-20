package com.gatekeeper.github.dto;

import com.gatekeeper.github.GitHubInstallation;
import com.gatekeeper.github.GitHubInstallationStatus;
import java.time.Instant;

/**
 * One GitHub App installation, as shown in the "GitHub Connections" section
 * of the Repositories page (Milestone 8: Repository Onboarding).
 * {@code repositoryCount} is denormalized from {@code RepositoryRepository}
 * rather than a JPA collection on {@link GitHubInstallation} - installations
 * are few and this is a read-only summary, not worth mapping a bidirectional
 * association for.
 */
public record GitHubInstallationResponse(
        Long id,
        Long installationId,
        String githubAccountLogin,
        String githubAccountType,
        String repositorySelection,
        GitHubInstallationStatus status,
        Instant lastSuccessfulSyncAt,
        String lastSyncError,
        boolean active,
        long repositoryCount,
        Instant createdAt) {

    public static GitHubInstallationResponse from(GitHubInstallation installation, long repositoryCount) {
        return new GitHubInstallationResponse(
                installation.getId(),
                installation.getInstallationId(),
                installation.getGithubAccountLogin(),
                installation.getGithubAccountType(),
                installation.getRepositorySelection(),
                installation.getStatus(),
                installation.getLastSuccessfulSyncAt(),
                installation.getLastSyncError(),
                installation.isActive(),
                repositoryCount,
                installation.getCreatedAt());
    }
}
