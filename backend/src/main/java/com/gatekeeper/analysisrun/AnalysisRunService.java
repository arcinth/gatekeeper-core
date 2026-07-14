package com.gatekeeper.analysisrun;

import com.gatekeeper.pullrequest.PullRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates AnalysisRuns for analyzed commits. Every new commit gets its own
 * AnalysisRun (docs/Domain-Model.md), but a webhook redelivery for a commit
 * already recorded must not create a second one - createIfAbsent is the only
 * write operation this service exposes, deliberately: there is no update path,
 * because Analysis Runs are immutable once created.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisRunService {

    private final AnalysisRunRepository analysisRunRepository;

    @Transactional
    public AnalysisRun createIfAbsent(PullRequest pullRequest, String commitSha, AnalysisRunTriggerReason triggerReason) {
        return analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), commitSha)
                .orElseGet(() -> insertOrRecoverFromRace(pullRequest, commitSha, triggerReason));
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
