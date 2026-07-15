package com.gatekeeper.analysisrun;

import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.pullrequest.PullRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates AnalysisRuns for analyzed commits and tracks their execution
 * lifecycle. Every new commit gets its own AnalysisRun (docs/Domain-Model.md),
 * but a webhook redelivery for a commit already recorded must not create a
 * second one - createIfAbsent handles that. Status transitions
 * (markQueued/markInProgress/markCompleted/markFailed, Milestone 4) are the
 * run's own real-time lifecycle progression, not a retroactive edit of a
 * completed run's results - see AnalysisRun's class Javadoc.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisRunService {

    private static final int FAILURE_REASON_MAX_LENGTH = 2000;

    private final AnalysisRunRepository analysisRunRepository;

    @Transactional
    public AnalysisRun createIfAbsent(PullRequest pullRequest, String commitSha, AnalysisRunTriggerReason triggerReason) {
        return analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), commitSha)
                .orElseGet(() -> insertOrRecoverFromRace(pullRequest, commitSha, triggerReason));
    }

    public AnalysisRun findByIdOrThrow(Long analysisRunId) {
        return analysisRunRepository.findById(analysisRunId)
                .orElseThrow(() -> new ResourceNotFoundException("AnalysisRun not found with id: " + analysisRunId));
    }

    /** Entity-in/entity-out: called by AnalysisOrchestrator on a run it already holds within its own transaction. */
    @Transactional
    public AnalysisRun markQueued(AnalysisRun run) {
        run.setStatus(AnalysisRunStatus.QUEUED);
        return analysisRunRepository.save(run);
    }

    /**
     * Loads by id (this runs in a fresh transaction on the async execution
     * thread, never the same one that created the run) and eagerly fetches
     * the associations AnalysisExecutionService needs to navigate afterward,
     * outside any transaction - see AnalysisRunRepository.findWithPullRequestAndRepositoryById.
     */
    @Transactional
    public AnalysisRun markInProgress(Long analysisRunId) {
        AnalysisRun run = analysisRunRepository.findWithPullRequestAndRepositoryById(analysisRunId)
                .orElseThrow(() -> new ResourceNotFoundException("AnalysisRun not found with id: " + analysisRunId));
        run.setStatus(AnalysisRunStatus.IN_PROGRESS);
        return analysisRunRepository.save(run);
    }

    /** Entity-in: called by PolicyFindingPersistenceService within the same transaction it loaded the run in. */
    @Transactional
    public void markCompleted(AnalysisRun run) {
        run.setStatus(AnalysisRunStatus.COMPLETED);
        analysisRunRepository.save(run);
    }

    @Transactional
    public void markFailed(Long analysisRunId, String reason) {
        AnalysisRun run = findByIdOrThrow(analysisRunId);
        run.setStatus(AnalysisRunStatus.FAILED);
        run.setFailureReason(truncate(reason));
        analysisRunRepository.save(run);
    }

    private String truncate(String reason) {
        if (reason == null) {
            return null;
        }
        return reason.length() > FAILURE_REASON_MAX_LENGTH ? reason.substring(0, FAILURE_REASON_MAX_LENGTH) : reason;
    }

    /**
     * Handles the case where a concurrent webhook redelivery for the same
     * (Pull Request, commit) inserts first: the unique constraint on
     * (pull_request_id, commit_sha) turns our insert into a
     * DataIntegrityViolationException instead of a duplicate AnalysisRun, and
     * that loss is not a real failure - the run is correctly recorded either way.
     */
    private AnalysisRun insertOrRecoverFromRace(PullRequest pullRequest, String commitSha, AnalysisRunTriggerReason triggerReason) {
        try {
            return analysisRunRepository.saveAndFlush(
                    AnalysisRun.builder()
                            .pullRequest(pullRequest)
                            .commitSha(commitSha)
                            .triggerReason(triggerReason)
                            .status(AnalysisRunStatus.RECEIVED)
                            .build());
        } catch (DataIntegrityViolationException ex) {
            return analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), commitSha)
                    .orElseThrow(() -> ex);
        }
    }
}
