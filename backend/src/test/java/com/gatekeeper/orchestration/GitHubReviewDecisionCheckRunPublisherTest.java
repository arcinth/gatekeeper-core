package com.gatekeeper.orchestration;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.gatekeeper.reviewdecision.ReviewDecisionRecordedEvent;
import org.junit.jupiter.api.Test;

class GitHubReviewDecisionCheckRunPublisherTest {

    private final GitHubReviewDecisionCheckRunService gitHubReviewDecisionCheckRunService =
            mock(GitHubReviewDecisionCheckRunService.class);
    private final GitHubReviewDecisionCheckRunPublisher publisher =
            new GitHubReviewDecisionCheckRunPublisher(gitHubReviewDecisionCheckRunService);

    @Test
    void onReviewDecisionRecorded_delegatesToTheService() {
        publisher.onReviewDecisionRecorded(new ReviewDecisionRecordedEvent(1L));

        verify(gitHubReviewDecisionCheckRunService).publishForReviewDecision(1L);
    }

    @Test
    void onReviewDecisionRecorded_swallowsAnyRuntimeExceptionFromTheService() {
        doThrow(new RuntimeException("GitHub is down"))
                .when(gitHubReviewDecisionCheckRunService).publishForReviewDecision(1L);

        assertThatCode(() -> publisher.onReviewDecisionRecorded(new ReviewDecisionRecordedEvent(1L)))
                .doesNotThrowAnyException();
    }
}
