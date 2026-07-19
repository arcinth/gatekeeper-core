package com.gatekeeper.github;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gatekeeper.github.dto.InstallationWebhookPayload;
import com.gatekeeper.organization.OrganizationService;
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
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubInstallationService {

    private static final String DELETED_ACTION = "deleted";

    private final GitHubInstallationRepository gitHubInstallationRepository;
    private final OrganizationService organizationService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void handleInstallationEvent(InstallationWebhookPayload payload, String deliveryId) {
        Long installationId = payload.installation().id();

        if (DELETED_ACTION.equals(payload.action())) {
            deactivate(installationId, deliveryId);
            return;
        }

        upsert(payload, deliveryId);
    }

    private void upsert(InstallationWebhookPayload payload, String deliveryId) {
        InstallationWebhookPayload.InstallationData data = payload.installation();
        InstallationWebhookPayload.AccountData account = data.account();

        GitHubInstallation installation = gitHubInstallationRepository.findByInstallationId(data.id())
                .orElseGet(() -> GitHubInstallation.builder()
                        .organization(organizationService.getDefaultOrganization())
                        .installationId(data.id())
                        .build());

        installation.setGithubAccountLogin(account.login());
        installation.setGithubAccountId(account.id());
        installation.setGithubAccountType(account.type());
        installation.setRepositorySelection(data.repositorySelection());
        installation.setPermissions(writePermissionsAsJson(data.permissions()));
        installation.setActive(true);

        gitHubInstallationRepository.save(installation);
        log.info("Installation {} upserted for account '{}' (delivery {}, action '{}').",
                installation.getInstallationId(), account.login(), deliveryId, payload.action());

        eventPublisher.publishEvent(new InstallationRepositorySyncRequestedEvent(installation.getInstallationId()));
    }

    private void deactivate(Long installationId, String deliveryId) {
        gitHubInstallationRepository.findByInstallationId(installationId).ifPresentOrElse(
                installation -> {
                    installation.setActive(false);
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
}
