package com.gatekeeper.analysisrun;

import com.gatekeeper.analysisrun.dto.AnalysisRunDetailResponse;
import com.gatekeeper.analysisrun.dto.AnalysisRunFilter;
import com.gatekeeper.analysisrun.dto.AnalysisRunSummaryResponse;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.pullrequest.PullRequest;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
 *
 * <p>Milestone 5 adds the read side (findSummaryPage/findDetailByIdOrThrow)
 * to this same service rather than a parallel query service, since this class
 * already mixes read (findByIdOrThrow) and write methods under one
 * transactional policy - see Milestone 5 Architecture, Section 7 / ADR-020.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisRunService {

    private static final int FAILURE_REASON_MAX_LENGTH = 2000;

    private final AnalysisRunRepository analysisRunRepository;
    private final PolicyFindingRepository policyFindingRepository;

    @Transactional
    public AnalysisRun createIfAbsent(PullRequest pullRequest, String commitSha, AnalysisRunTriggerReason triggerReason) {
        return analysisRunRepository.findByPullRequestIdAndCommitSha(pullRequest.getId(), commitSha)
                .orElseGet(() -> insertOrRecoverFromRace(pullRequest, commitSha, triggerReason));
    }

    public AnalysisRun findByIdOrThrow(Long analysisRunId) {
        return analysisRunRepository.findById(analysisRunId)
                .orElseThrow(() -> new ResourceNotFoundException("AnalysisRun not found with id: " + analysisRunId));
    }

    /**
     * Fetches a filtered/sorted page of runs, then enriches each row with its
     * findings count via one supplementary batched query over the page's ids
     * (Milestone 5 Architecture, Section 8 / ADR-021) - deliberately not a
     * single combined Specification + fetch-join + GROUP BY + pagination query,
     * which is fragile to get right under dynamic filters.
     */
    public Page<AnalysisRunSummaryResponse> findSummaryPage(AnalysisRunFilter filter, Pageable pageable) {
        Page<AnalysisRun> page = analysisRunRepository.findAll(AnalysisRunSpecifications.matching(filter), pageable);
        Map<Long, Long> findingsTotals = findingsTotalsByAnalysisRunId(page.getContent());
        return page.map(run -> AnalysisRunSummaryResponse.from(run, findingsTotals.getOrDefault(run.getId(), 0L)));
    }

    public AnalysisRunDetailResponse findDetailByIdOrThrow(Long analysisRunId) {
        AnalysisRun run = analysisRunRepository.findWithPullRequestAndRepositoryById(analysisRunId)
                .orElseThrow(() -> new ResourceNotFoundException("AnalysisRun not found with id: " + analysisRunId));
        Map<PolicySeverity, Long> findingsBySeverity = new EnumMap<>(PolicySeverity.class);
        for (Object[] row : policyFindingRepository.countBySeverityForAnalysisRun(analysisRunId)) {
            findingsBySeverity.put((PolicySeverity) row[0], (Long) row[1]);
        }
        return AnalysisRunDetailResponse.from(run, findingsBySeverity);
    }

    private Map<Long, Long> findingsTotalsByAnalysisRunId(List<AnalysisRun> runs) {
        if (runs.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = runs.stream().map(AnalysisRun::getId).toList();
        Map<Long, Long> totals = new HashMap<>();
        for (Object[] row : policyFindingRepository.countByAnalysisRunIdIn(ids)) {
            totals.put((Long) row[0], (Long) row[1]);
        }
        return totals;
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
