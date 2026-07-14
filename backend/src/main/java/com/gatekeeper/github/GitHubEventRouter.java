package com.gatekeeper.github;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.gatekeeper.github.dto.PullRequestWebhookPayload;
import com.gatekeeper.github.exception.MalformedWebhookPayloadException;
import com.gatekeeper.orchestration.AnalysisOrchestrator;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Dispatches a verified webhook delivery by its X-GitHub-Event type. GitHub
 * sends many event types GateKeeper doesn't act on (issues, star, push, ...);
 * this is the single place that decides which ones matter, so
 * GitHubWebhookController stays focused on HTTP/signature concerns and
 * AnalysisOrchestrator never has to know it was reached via a webhook at all.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubEventRouter {

    private static final String PULL_REQUEST_EVENT = "pull_request";

    private final AnalysisOrchestrator analysisOrchestrator;
    private final ObjectMapper objectMapper;

    public void route(String eventType, byte[] payload, String deliveryId) {
        if (!PULL_REQUEST_EVENT.equals(eventType)) {
            log.info("Ignoring unsupported GitHub event type '{}' (delivery {}).", eventType, deliveryId);
            return;
        }

        analysisOrchestrator.handlePullRequestEvent(parsePullRequestPayload(payload), deliveryId);
    }

    private PullRequestWebhookPayload parsePullRequestPayload(byte[] payload) {
        try {
            return objectMapper.readValue(payload, PullRequestWebhookPayload.class);
        } catch (MismatchedInputException ex) {
            throw new MalformedWebhookPayloadException(
                    "pull_request webhook payload does not match the expected shape.", ex);
        } catch (IOException ex) {
            throw new MalformedWebhookPayloadException("pull_request webhook payload is not valid JSON.", ex);
        }
    }
}
