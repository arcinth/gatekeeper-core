package com.gatekeeper.github;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GitHubInstallationRepository extends JpaRepository<GitHubInstallation, Long> {

    Optional<GitHubInstallation> findByInstallationId(Long installationId);
}
