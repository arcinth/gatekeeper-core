package com.gatekeeper.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.gatekeeper.github.dto.InstallationRepositoriesWebhookPayload;
import com.gatekeeper.github.dto.InstallationWebhookPayload;
import com.gatekeeper.github.dto.PullRequestWebhookPayload;
import com.gatekeeper.github.exception.MalformedWebhookPayloadException;
import com.gatekeeper.orchestration.AnalysisOrchestrator;
import com.gatekeeper.repository.RepositoryService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Dispatches a verified webhook delivery by its X-GitHub-Event type. GitHub
 * sends many event types GateKeeper doesn't act on (issues, star, push, ...);
 * this is the single place that decides which ones matter, so
 * GitHubWebhookController stays focused on HTTP/signature concerns and the
 * downstream handlers never have to know they were reached via a webhook
 * at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubEventRouter {

    private static final String PULL_REQUEST_EVENT = "pull_request";
    private static final String INSTALLATION_EVENT = "installation";
    private static final String INSTALLATION_REPOSITORIES_EVENT = "installation_repositories";

    private final AnalysisOrchestrator analysisOrchestrator;
    private final GitHubInstallationService gitHubInstallationService;
    private final RepositoryService repositoryService;
    private final ObjectMapper objectMapper;

    public void route(String eventType, byte[] payload, String deliveryId) {
        if (PULL_REQUEST_EVENT.equals(eventType)) {
            analysisOrchestrator.handlePullRequestEvent(
                    parsePayload(payload, PullRequestWebhookPayload.class), deliveryId);
            return;
        }
        if (INSTALLATION_EVENT.equals(eventType)) {
            gitHubInstallationService.handleInstallationEvent(
                    parsePayload(payload, InstallationWebhookPayload.class), deliveryId);
            return;
        }
        if (INSTALLATION_REPOSITORIES_EVENT.equals(eventType)) {
            repositoryService.handleInstallationRepositoriesEvent(
                    parsePayload(payload, InstallationRepositoriesWebhookPayload.class), deliveryId);
            return;
        }

        log.info("Ignoring unsupported GitHub event type '{}' (delivery {}).", eventType, deliveryId);
    }

    private <T> T parsePayload(byte[] payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (MismatchedInputException ex) {
            throw new MalformedWebhookPayloadException(
                    type.getSimpleName() + " does not match the expected shape.", ex);
        } catch (IOException ex) {
            throw new MalformedWebhookPayloadException(type.getSimpleName() + " is not valid JSON.", ex);
        }
    }
}
