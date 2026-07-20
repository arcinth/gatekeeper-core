package com.gatekeeper.github;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Reports GitHub integration health from data GateKeeper already has, not a
 * live call to api.github.com (Milestone 9: Observability - a live call on
 * every health poll would be slow, rate-limit-risky, and would make
 * readiness flap on a transient GitHub blip that has nothing to do with
 * whether GateKeeper itself is healthy).
 * <p>
 * Reuses {@link GitHubInstallationStatus} (Milestone 8: Repository
 * Onboarding), which {@link GitHubRepositorySyncService} already maintains
 * from real synchronization attempts - this indicator is a pure read of that
 * existing signal, not a new one. Deliberately never reports {@code DOWN}:
 * one or more installations sitting in {@code ERROR} is real, actionable
 * information (surfaced in the detail map) but is not by itself a reason to
 * fail GateKeeper's own readiness/liveness - that must not flap because a
 * customer's GitHub installation is unhealthy, mirroring the same reasoning
 * {@code RepositoryLookupService} already applies to a webhook for an
 * as-yet-unlinked repository (not an error, an expected state).
 */
@Component("github")
public class GitHubHealthIndicator implements HealthIndicator {

    private final long appId;
    private final GitHubInstallationRepository gitHubInstallationRepository;

    public GitHubHealthIndicator(
            @Value("${gatekeeper.github.app.id}") long appId,
            GitHubInstallationRepository gitHubInstallationRepository) {
        this.appId = appId;
        this.gitHubInstallationRepository = gitHubInstallationRepository;
    }

    @Override
    public Health health() {
        if (appId == 0) {
            return Health.unknown()
                    .withDetail("reason", "GitHub App is not configured (gatekeeper.github.app.id is unset).")
                    .build();
        }

        List<GitHubInstallation> installations = gitHubInstallationRepository.findAll();
        Map<GitHubInstallationStatus, Long> countsByStatus = installations.stream()
                .collect(Collectors.groupingBy(GitHubInstallation::getStatus, Collectors.counting()));

        return Health.up()
                .withDetail("totalInstallations", installations.size())
                .withDetail("activeInstallations", countsByStatus.getOrDefault(GitHubInstallationStatus.ACTIVE, 0L))
                .withDetail("errorInstallations", countsByStatus.getOrDefault(GitHubInstallationStatus.ERROR, 0L))
                .withDetail("connectingInstallations", countsByStatus.getOrDefault(GitHubInstallationStatus.CONNECTING, 0L))
                .withDetail("disconnectedInstallations", countsByStatus.getOrDefault(GitHubInstallationStatus.DISCONNECTED, 0L))
                .build();
    }
}
