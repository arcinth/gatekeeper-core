package com.gatekeeper.orchestration;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.github.dto.PullRequestWebhookPayload;
import com.gatekeeper.github.exception.MalformedWebhookPayloadException;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestService;
import com.gatekeeper.pullrequest.PullRequestUpsertCommand;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.repository.RepositoryLookupService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Drives a verified "pull_request" webhook event through the ingestion
 * pipeline: resolve the Repository it belongs to, persist the PullRequest,
 * and create an AnalysisRun for the current commit (Sprint 2 Architecture,
 * Section 10). Once queued, execution (Policy Engine and beyond) is
 * AnalysisExecutionService's concern, triggered asynchronously via
 * AnalysisRunReadyForExecutionEvent - this class's job ends at handing off
 * a durably-queued run (Milestone 4 Architecture, Section 3 / ADR-013).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalysisOrchestrator {

    private final RepositoryLookupService repositoryLookupService;
    private final PullRequestService pullRequestService;
    private final AnalysisRunService analysisRunService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public void handlePullRequestEvent(PullRequestWebhookPayload payload, String deliveryId) {
        validate(payload);

        Optional<PullRequestAction> action = PullRequestAction.fromGitHubAction(payload.action());
        if (action.isEmpty()) {
            log.info("Ignoring pull_request action '{}' (delivery {}).", payload.action(), deliveryId);
            return;
        }

        Optional<Repository> repository = repositoryLookupService.findLinkedRepository(payload.repository().id());
        if (repository.isEmpty()) {
            log.info("Skipping delivery {}: GitHub repository id {} has no linked GateKeeper repository.",
                    deliveryId, payload.repository().id());
            return;
        }

        warnIfInstallationMismatch(payload, repository.get(), deliveryId);

        PullRequest pullRequest = pullRequestService.upsert(repository.get(), toUpsertCommand(payload.pullRequest()));
        log.info("Persisted PR #{} ({}) for repository '{}' (delivery {}).",
                pullRequest.getNumber(), pullRequest.getStatus(), repository.get().getFullName(), deliveryId);

        if (action.get().createsAnalysisRun()) {
            AnalysisRun run = analysisRunService.createIfAbsent(
                    pullRequest, payload.pullRequest().head().sha(), action.get().triggerReason());
            log.info("AnalysisRun {} ({}) recorded for PR #{} at commit {} (delivery {}).",
                    run.getId(), run.getStatus(), pullRequest.getNumber(), run.getCommitSha(), deliveryId);
            queueForExecutionIfNewlyCreated(run, deliveryId);
        }
    }

    /**
     * createIfAbsent is idempotent by design (Milestone 2): a webhook
     * redelivery for a commit already recorded returns the existing run
     * unchanged rather than creating a duplicate. RECEIVED is only ever the
     * status of a run this call just created - anything else means this run
     * already went through (or is still going through) the pipeline, so
     * re-queuing it here would execute the Policy Engine a second time and
     * insert duplicate findings. Checking status, rather than changing
     * createIfAbsent's signature, keeps that already-approved method untouched.
     */
    private void queueForExecutionIfNewlyCreated(AnalysisRun run, String deliveryId) {
        if (run.getStatus() != AnalysisRunStatus.RECEIVED) {
            log.info("AnalysisRun {} is already {} - not re-queuing (delivery {}).",
                    run.getId(), run.getStatus(), deliveryId);
            return;
        }

        AnalysisRun queued = analysisRunService.markQueued(run);
        eventPublisher.publishEvent(new AnalysisRunReadyForExecutionEvent(queued.getId()));
        log.info("AnalysisRun {} queued for execution (delivery {}).", queued.getId(), deliveryId);
    }

    /**
     * GitHub always sends a complete pull_request/repository object for this
     * event type, but a malformed test payload or an unexpected GitHub API
     * change should fail loudly and early rather than NPE deep in a mapper.
     */
    private void validate(PullRequestWebhookPayload payload) {
        if (payload.action() == null) {
            throw new MalformedWebhookPayloadException("pull_request webhook payload is missing 'action'.");
        }
        if (payload.repository() == null || payload.repository().id() == null) {
            throw new MalformedWebhookPayloadException("pull_request webhook payload is missing 'repository.id'.");
        }
        PullRequestWebhookPayload.PullRequestData pr = payload.pullRequest();
        if (pr == null || pr.id() == null || pr.number() == null) {
            throw new MalformedWebhookPayloadException("pull_request webhook payload is missing 'pull_request' details.");
        }
        if (pr.head() == null || pr.head().sha() == null || pr.base() == null) {
            throw new MalformedWebhookPayloadException("pull_request webhook payload is missing head/base branch details.");
        }
    }

    /**
     * The webhook signature already proves this payload came from GitHub, so a
     * mismatch here is a data-consistency signal to investigate (e.g. a
     * repository transferred between installations), not a trust boundary -
     * processing still proceeds rather than silently dropping a real PR.
     */
    private void warnIfInstallationMismatch(PullRequestWebhookPayload payload, Repository repository, String deliveryId) {
        Long payloadInstallationId = payload.installation() != null ? payload.installation().id() : null;
        Long linkedInstallationId = repository.getGithubInstallation().getInstallationId();
        if (payloadInstallationId != null && !payloadInstallationId.equals(linkedInstallationId)) {
            log.warn("Delivery {}: webhook installation id {} does not match repository '{}''s linked installation {}.",
                    deliveryId, payloadInstallationId, repository.getFullName(), linkedInstallationId);
        }
    }

    private PullRequestUpsertCommand toUpsertCommand(PullRequestWebhookPayload.PullRequestData data) {
        return new PullRequestUpsertCommand(
                data.id(),
                data.number(),
                data.title(),
                data.user() != null ? data.user().login() : "unknown",
                data.head().ref(),
                data.base().ref(),
                data.head().sha(),
                data.state(),
                data.merged());
    }
}
