package com.gatekeeper.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.github.dto.InstallationWebhookPayload;
import com.gatekeeper.organization.OrganizationService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists GitHub App installation onboarding events: creates or updates the
 * GitHubInstallation row an "installation" webhook describes, or marks it
 * inactive on uninstall. GitHub sends the installation's full current state
 * on every action (not a diff), so every action other than "deleted" is
 * handled identically as an upsert - "new_permissions_accepted" naturally
 * refreshes the permissions column this same way, with no special case
 * needed.
 * <p>
 * Every successful upsert also publishes InstallationRepositorySyncRequestedEvent
 * (not on deactivate - nothing to synchronize on uninstall). This class only
 * ever learns an installation's own identity from the webhook payload; which
 * repositories that installation can see is a separate question the webhook
 * payload cannot reliably answer (see GitHubRepositorySyncService), so it is
 * delegated via this event rather than answered here.
 * <p>
 * Milestone 8 (Repository Onboarding) adds: read methods for
 * GitHubInstallationController, and the three mark* status-transition methods
 * GitHubRepositorySyncService calls around its own (deliberately
 * non-transactional) external API call - each is its own short transaction,
 * called on this bean (not self-invoked), so the transaction proxy actually
 * applies. See GitHubInstallationStatus's Javadoc for the full state machine.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubInstallationService {

    private static final String DELETED_ACTION = "deleted";
    private static final int MAX_SYNC_ERROR_LENGTH = 1000;

    private final GitHubInstallationRepository gitHubInstallationRepository;
    private final OrganizationService organizationService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional
    public void handleInstallationEvent(InstallationWebhookPayload payload, String deliveryId) {
        Long installationId = payload.installation().id();

        if (DELETED_ACTION.equals(payload.action())) {
            deactivate(installationId, deliveryId);
            return;
        }

        upsert(payload, deliveryId);
    }

    public List<GitHubInstallation> findAll() {
        return gitHubInstallationRepository.findAll();
    }

    public GitHubInstallation findByIdOrThrow(Long id) {
        return gitHubInstallationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("GitHub installation not found with id: " + id));
    }

    public GitHubInstallation findByInstallationIdOrThrow(Long installationId) {
        return gitHubInstallationRepository.findByInstallationId(installationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "GitHub installation not found with installation id: " + installationId));
    }

    /** Called by GitHubRepositorySyncService immediately before it calls GitHub - a no-op if the installation vanished mid-flight. */
    @Transactional
    public void markSyncing(Long installationId) {
        gitHubInstallationRepository.findByInstallationId(installationId).ifPresent(installation -> {
            installation.setStatus(GitHubInstallationStatus.SYNCING);
            gitHubInstallationRepository.save(installation);
        });
    }

    /** Called on a successful synchronization - clears any prior error and stamps the success time. */
    @Transactional
    public void markSynced(Long installationId) {
        gitHubInstallationRepository.findByInstallationId(installationId).ifPresent(installation -> {
            installation.setStatus(GitHubInstallationStatus.ACTIVE);
            installation.setLastSuccessfulSyncAt(clock.instant());
            installation.setLastSyncError(null);
            gitHubInstallationRepository.save(installation);
        });
    }

    /** Called when synchronization throws - the UI surfaces lastSyncError instead of the installation silently going stale. */
    @Transactional
    public void markSyncFailed(Long installationId, String errorMessage) {
        gitHubInstallationRepository.findByInstallationId(installationId).ifPresent(installation -> {
            installation.setStatus(GitHubInstallationStatus.ERROR);
            installation.setLastSyncError(truncate(errorMessage));
            gitHubInstallationRepository.save(installation);
        });
    }

    private void upsert(InstallationWebhookPayload payload, String deliveryId) {
        InstallationWebhookPayload.InstallationData data = payload.installation();
        InstallationWebhookPayload.AccountData account = data.account();

        GitHubInstallation installation = gitHubInstallationRepository.findByInstallationId(data.id()).orElse(null);
        boolean isNew = installation == null;
        if (isNew) {
            installation = GitHubInstallation.builder()
                    .organization(organizationService.getDefaultOrganization())
                    .installationId(data.id())
                    .build();
        }

        installation.setGithubAccountLogin(account.login());
        installation.setGithubAccountId(account.id());
        installation.setGithubAccountType(account.type());
        installation.setRepositorySelection(data.repositorySelection());
        installation.setPermissions(writePermissionsAsJson(data.permissions()));
        installation.setActive(true);
        // A reinstall/unsuspend of a previously-disconnected installation starts the
        // lifecycle over - it has no synchronized repositories yet until the sync this
        // upsert triggers below completes. A row already mid-lifecycle (ACTIVE/SYNCING/
        // ERROR) keeps its own status untouched here; only the sync itself moves it.
        if (isNew || installation.getStatus() == GitHubInstallationStatus.DISCONNECTED) {
            installation.setStatus(GitHubInstallationStatus.CONNECTING);
        }

        gitHubInstallationRepository.save(installation);
        log.info("Installation {} upserted for account '{}' (delivery {}, action '{}').",
                installation.getInstallationId(), account.login(), deliveryId, payload.action());

        eventPublisher.publishEvent(new InstallationRepositorySyncRequestedEvent(installation.getInstallationId()));
    }

    private void deactivate(Long installationId, String deliveryId) {
        gitHubInstallationRepository.findByInstallationId(installationId).ifPresentOrElse(
                installation -> {
                    installation.setActive(false);
                    installation.setStatus(GitHubInstallationStatus.DISCONNECTED);
                    gitHubInstallationRepository.save(installation);
                    log.info("Installation {} marked inactive (delivery {}).", installationId, deliveryId);
                },
                () -> log.info("Ignoring deletion for unknown installation {} (delivery {}).",
                        installationId, deliveryId));
    }

    private String writePermissionsAsJson(Map<String, String> permissions) {
        if (permissions == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(permissions);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize installation permissions.", ex);
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_SYNC_ERROR_LENGTH ? message.substring(0, MAX_SYNC_ERROR_LENGTH) : message;
    }
}
