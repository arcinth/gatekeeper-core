package com.gatekeeper.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryRepository extends JpaRepository<Repository, Long> {

    Optional<Repository> findByGithubRepositoryId(Long githubRepositoryId);

    /** Powers the GitHub Connections section's per-installation repository count (Milestone 8: Repository Onboarding). */
    long countByGithubInstallationId(Long githubInstallationId);
}
