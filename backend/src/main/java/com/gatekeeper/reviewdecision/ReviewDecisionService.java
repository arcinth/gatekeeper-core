package com.gatekeeper.reviewdecision;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.reviewdecision.dto.CreateReviewDecisionRequest;
import com.gatekeeper.reviewdecision.dto.ReviewDecisionResponse;
import com.gatekeeper.user.User;
import com.gatekeeper.user.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records and lists ReviewDecisions for an AnalysisRun (Milestone 2). Purely
 * additive: neither {@link #create} nor {@link #findHistory} reads or writes
 * anything on AnalysisRun, Verdict, or PullRequest - a decision is observed
 * data layered on top of the deterministic pipeline's own output, not a
 * gate on it (see V12__review_decisions.sql). No role check on who may
 * submit a decision and no self-review restriction - deliberate scope
 * exclusions for this milestone.
 * <p>
 * {@code create} publishes {@link ReviewDecisionRecordedEvent} at the end of
 * its own transaction (Milestone 4), the same "publish once the write has
 * happened" convention {@code AnalysisResultPersistenceService} already
 * established for {@code VerdictProducedEvent} - see
 * {@code GitHubReviewDecisionCheckRunPublisher}'s Javadoc for why its
 * {@code @Async}/{@code AFTER_COMMIT} listener is required for correctness,
 * not an optimization. This class itself still never reads or writes
 * anything GitHub-related directly - it only announces that a decision now
 * exists.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReviewDecisionService {

    private final ReviewDecisionRepository reviewDecisionRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ReviewDecisionResponse create(Long analysisRunId, Long reviewerId, CreateReviewDecisionRequest request) {
        AnalysisRun analysisRun = analysisRunRepository.findById(analysisRunId)
                .orElseThrow(() -> new ResourceNotFoundException("AnalysisRun not found with id: " + analysisRunId));
        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + reviewerId));

        ReviewDecision reviewDecision = reviewDecisionRepository.save(ReviewDecision.builder()
                .analysisRun(analysisRun)
                .reviewer(reviewer)
                .decision(request.decision())
                .comment(request.comment())
                .build());

        eventPublisher.publishEvent(new ReviewDecisionRecordedEvent(analysisRunId));
        return ReviewDecisionResponse.from(reviewDecision);
    }

    public List<ReviewDecisionResponse> findHistory(Long analysisRunId) {
        if (!analysisRunRepository.existsById(analysisRunId)) {
            throw new ResourceNotFoundException("AnalysisRun not found with id: " + analysisRunId);
        }
        return reviewDecisionRepository.findByAnalysisRunIdOrderByCreatedAtDesc(analysisRunId).stream()
                .map(ReviewDecisionResponse::from)
                .toList();
    }
}
