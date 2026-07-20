package com.gatekeeper.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.gatekeeper.github.dto.InstallationRepositoriesWebhookPayload;
import com.gatekeeper.github.dto.InstallationWebhookPayload;
import com.gatekeeper.github.dto.PullRequestWebhookPayload;
import com.gatekeeper.github.exception.MalformedWebhookPayloadException;
import com.gatekeeper.orchestration.AnalysisOrchestrator;
import com.gatekeeper.repository.RepositoryService;
import io.micrometer.core.instrument.MeterRegistry;
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
 * <p>
 * Records {@code gatekeeper.webhook.events} (Milestone 9: Observability),
 * tagged by {@code event_type} (GitHub's own fixed, finite event catalog -
 * bounded cardinality, not user-controlled) and {@code outcome}
 * (processed/ignored). A parsing/signature failure is not double-counted
 * here - it already increments {@code gatekeeper.errors.total} via
 * GlobalExceptionHandler, which is where every handled-exception metric
 * lives; this counter only distinguishes "GateKeeper acted on this event"
 * from "GateKeeper deliberately ignored it."
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
    private final MeterRegistry meterRegistry;

    public void route(String eventType, byte[] payload, String deliveryId) {
        if (PULL_REQUEST_EVENT.equals(eventType)) {
            analysisOrchestrator.handlePullRequestEvent(
                    parsePayload(payload, PullRequestWebhookPayload.class), deliveryId);
            recordEvent(eventType, "processed");
            return;
        }
        if (INSTALLATION_EVENT.equals(eventType)) {
            gitHubInstallationService.handleInstallationEvent(
                    parsePayload(payload, InstallationWebhookPayload.class), deliveryId);
            recordEvent(eventType, "processed");
            return;
        }
        if (INSTALLATION_REPOSITORIES_EVENT.equals(eventType)) {
            repositoryService.handleInstallationRepositoriesEvent(
                    parsePayload(payload, InstallationRepositoriesWebhookPayload.class), deliveryId);
            recordEvent(eventType, "processed");
            return;
        }

        log.info("Ignoring unsupported GitHub event type '{}' (delivery {}).", eventType, deliveryId);
        recordEvent(eventType == null ? "unknown" : eventType, "ignored");
    }

    private void recordEvent(String eventType, String outcome) {
        meterRegistry.counter("gatekeeper.webhook.events", "event_type", eventType, "outcome", outcome).increment();
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
