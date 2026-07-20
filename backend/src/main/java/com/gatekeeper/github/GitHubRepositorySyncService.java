package com.gatekeeper.github;

import com.gatekeeper.github.dto.InstallationRepositoriesResponse;
import com.gatekeeper.repository.RepositoryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Closes the gap neither GitHub webhook can be relied on to close alone:
 * "installation" tells GateKeeper an installation exists, but not reliably
 * which repositories it can see (particularly the initial set at install
 * time, under repository_selection "all"); "installation_repositories"
 * reports later changes to that selection, but is not guaranteed to fire for
 * the initial one. GitHub's own documented answer to "what can this
 * installation see right now" is GET /installation/repositories - this class
 * calls it and hands the result to RepositoryService, the same repository
 * upsert path the installation_repositories webhook already uses.
 * <p>
 * Mirrors AnalysisExecutionService's shape, not ReportGenerationListener's:
 * {@link #synchronize} itself is not {@code @Transactional} (a database
 * connection must not be held across the external GitHub API call - the same
 * ADR-016 reasoning AnalysisExecutionService's own Javadoc documents), so
 * there is no self-invocation hazard in also making it this class's
 * {@code @Async} listener entry point, unlike GitHubCheckRunPublisher/
 * GitHubCheckRunService's split. The actual persistence
 * (RepositoryService.synchronizeFromInstallation) is {@code @Transactional}
 * on that other bean, called normally through its own proxy - as are the
 * GitHubInstallationService.mark* status-transition calls added in
 * Milestone 8 (Repository Onboarding), for the identical reason.
 * <p>
 * {@code @Async} (default executor - analysisExecutionTaskExecutor) for the
 * same reason AnalysisExecutionService and AIReviewExecutionService are:
 * GitHub's webhook delivery has its own timeout, and this must not hold the
 * "installation" webhook's HTTP response open while paginating GitHub's API.
 * Every exception is caught here rather than propagated, for the same
 * AFTER_COMMIT reason ReportGenerationListener's Javadoc documents in full -
 * a sync failure must never affect the GitHubInstallation row that already
 * committed; it only moves that row's status to ERROR so the failure is
 * visible instead of silent.
 * <p>
 * {@link #synchronize} is deliberately {@code public} (Milestone 8): besides
 * the async webhook-triggered path above, GitHubInstallationController calls
 * it synchronously so the onboarding callback page and a manual "Resync now"
 * action get an immediate result instead of waiting on the webhook/event
 * round trip - the exact same method, no parallel implementation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubRepositorySyncService {

    private final GitHubInstallationRepository gitHubInstallationRepository;
    private final GitHubAppAuthService gitHubAppAuthService;
    private final GitHubApiClient gitHubApiClient;
    private final RepositoryService repositoryService;
    private final GitHubInstallationService gitHubInstallationService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInstallationRepositorySyncRequested(InstallationRepositorySyncRequestedEvent event) {
        synchronize(event.installationId());
    }

    /** Returns the number of repositories GitHub reported for this installation, or 0 if it doesn't exist or the sync failed. */
    public int synchronize(Long installationId) {
        if (gitHubInstallationRepository.findByInstallationId(installationId).isEmpty()) {
            log.warn("Cannot synchronize repositories: installation {} no longer exists.", installationId);
            return 0;
        }

        gitHubInstallationService.markSyncing(installationId);

        try {
            String installationAccessToken = gitHubAppAuthService.getInstallationAccessToken(installationId);
            List<InstallationRepositoriesResponse.RepositorySummary> repositories =
                    gitHubApiClient.listInstallationRepositories(installationAccessToken);
            repositoryService.synchronizeFromInstallation(installationId, repositories);
            gitHubInstallationService.markSynced(installationId);
            return repositories.size();
        } catch (RuntimeException ex) {
            log.error("Repository synchronization failed for installation {}; repositories may be stale until the "
                            + "next installation event or an installation_repositories webhook arrives.",
                    installationId, ex);
            gitHubInstallationService.markSyncFailed(installationId, ex.getMessage());
            return 0;
        }
    }
}
