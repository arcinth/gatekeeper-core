package com.gatekeeper.orchestration;

import com.gatekeeper.reviewdecision.ReviewDecisionRecordedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Thin dispatcher from {@link ReviewDecisionRecordedEvent} to
 * {@link GitHubReviewDecisionCheckRunService} - mirrors
 * {@link GitHubCheckRunPublisher}'s own split from {@code GitHubCheckRunService}
 * exactly, for the identical reason: this listener method is called
 * externally by Spring's event multicaster, so delegating to a different
 * bean's {@code @Transactional} method here is a normal, safe proxy call, not
 * self-invocation.
 * <p>
 * <b>{@code @Async} is required for correctness here, not an optimization</b> -
 * see {@link GitHubCheckRunPublisher#onVerdictProduced}'s Javadoc for the full
 * explanation ({@code @TransactionalEventListener(phase = AFTER_COMMIT)}
 * callbacks run before the committing transaction's {@code EntityManagerHolder}
 * is unbound; running synchronously would silently join that
 * already-committed transaction, and every write this method makes
 * (recording {@code githubReviewCheckRunId}) would be discarded with no
 * exception anywhere). The same reasoning applies verbatim here.
 * <p>
 * <b>Every exception from GitHubReviewDecisionCheckRunService is caught here,
 * not propagated</b>: a GitHub API failure must never affect the
 * already-committed {@code ReviewDecision} - it stays recorded and stands on
 * its own regardless of whether GitHub could be reached.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubReviewDecisionCheckRunPublisher {

    private final GitHubReviewDecisionCheckRunService gitHubReviewDecisionCheckRunService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReviewDecisionRecorded(ReviewDecisionRecordedEvent event) {
        try {
            gitHubReviewDecisionCheckRunService.publishForReviewDecision(event.analysisRunId());
        } catch (RuntimeException ex) {
            log.error("GitHub review check run publication failed for analysis run {} after a ReviewDecision was "
                            + "recorded; the ReviewDecision itself is unaffected.",
                    event.analysisRunId(), ex);
        }
    }
}
