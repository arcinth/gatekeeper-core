package com.gatekeeper.analysisrun;

import com.gatekeeper.analysisrun.dto.AnalysisRunDetailResponse;
import com.gatekeeper.analysisrun.dto.AnalysisRunFilter;
import com.gatekeeper.analysisrun.dto.AnalysisRunSummaryResponse;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import com.gatekeeper.verdict.Verdict;
import com.gatekeeper.verdict.VerdictReasonRepository;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdict.dto.VerdictReasonSummary;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 * Sprint 3 Milestone 3 extends both read methods to also enrich with Security
 * Engine findings, mirroring the Policy enrichment exactly rather than
 * introducing a parallel query path.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AnalysisRunService {

    private static final int FAILURE_REASON_MAX_LENGTH = 2000;

    private final AnalysisRunRepository analysisRunRepository;
    private final PolicyFindingRepository policyFindingRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final VerdictRepository verdictRepository;
    private final VerdictReasonRepository verdictReasonRepository;

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
     * Read-only counterpart to markInProgress: loads the same eager
     * pullRequest/repository/githubInstallation association graph, needed by
     * any caller that navigates those associations after this transaction
     * closes (see AnalysisRunRepository.findWithPullRequestAndRepositoryById),
     * but never mutates status. Added for AIReviewExecutionService (Sprint 4
     * Milestone 3), which must never touch AnalysisRun's own lifecycle -
     * AI Review failures must never affect the analysis pipeline's own
     * COMPLETED/FAILED outcome.
     */
    public AnalysisRun findWithPullRequestAndRepositoryByIdOrThrow(Long analysisRunId) {
        return analysisRunRepository.findWithPullRequestAndRepositoryById(analysisRunId)
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
        List<Long> ids = page.getContent().stream().map(AnalysisRun::getId).toList();
        Map<Long, Long> findingsTotals = countsByAnalysisRunId(ids, policyFindingRepository::countByAnalysisRunIdIn);
        Map<Long, Long> securityFindingsTotals =
                countsByAnalysisRunId(ids, securityFindingRepository::countByAnalysisRunIdIn);
        Map<Long, VerdictOutcome> verdictOutcomes = verdictOutcomesByAnalysisRunId(ids);
        return page.map(run -> AnalysisRunSummaryResponse.from(run,
                findingsTotals.getOrDefault(run.getId(), 0L),
                securityFindingsTotals.getOrDefault(run.getId(), 0L),
                verdictOutcomes.get(run.getId())));
    }

    public AnalysisRunDetailResponse findDetailByIdOrThrow(Long analysisRunId) {
        AnalysisRun run = analysisRunRepository.findWithPullRequestAndRepositoryById(analysisRunId)
                .orElseThrow(() -> new ResourceNotFoundException("AnalysisRun not found with id: " + analysisRunId));
        Map<PolicySeverity, Long> findingsBySeverity = new EnumMap<>(PolicySeverity.class);
        for (Object[] row : policyFindingRepository.countBySeverityForAnalysisRun(analysisRunId)) {
            findingsBySeverity.put((PolicySeverity) row[0], (Long) row[1]);
        }
        Map<SecuritySeverity, Long> securityFindingsBySeverity = new EnumMap<>(SecuritySeverity.class);
        for (Object[] row : securityFindingRepository.countBySeverityForAnalysisRun(analysisRunId)) {
            securityFindingsBySeverity.put((SecuritySeverity) row[0], (Long) row[1]);
        }

        VerdictOutcome verdictOutcome = null;
        List<VerdictReasonSummary> verdictReasons = List.of();
        Optional<Verdict> verdict = verdictRepository.findByAnalysisRunId(analysisRunId);
        if (verdict.isPresent()) {
            verdictOutcome = verdict.get().getOutcome();
            verdictReasons = verdictReasonRepository.findByVerdictIdOrderById(verdict.get().getId()).stream()
                    .map(VerdictReasonSummary::from)
                    .toList();
        }

        return AnalysisRunDetailResponse.from(
                run, findingsBySeverity, securityFindingsBySeverity, verdictOutcome, verdictReasons);
    }

    /**
     * Batched per-run verdict outcome lookup for findSummaryPage's
     * enrichment - mirrors countsByAnalysisRunId's empty-page guard, but
     * returns full Verdict entities (mapped down to just their outcome)
     * rather than counts, since a list row needs the actual outcome value,
     * not how many verdicts exist (always zero or one, per ADR-039).
     */
    private Map<Long, VerdictOutcome> verdictOutcomesByAnalysisRunId(List<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return verdictRepository.findByAnalysisRunIdIn(ids).stream()
                .collect(Collectors.toMap(v -> v.getAnalysisRun().getId(), Verdict::getOutcome));
    }

    /**
     * Skips calling the batch-count query entirely for an empty page, rather
     * than issuing an "IN ()" query - the same guard both the Policy and
     * Security batch-count call sites needed, now shared by them here. The
     * query itself is passed as a function so the guard applies before either
     * repository is actually invoked, not just before its result is used.
     */
    private Map<Long, Long> countsByAnalysisRunId(List<Long> ids, Function<Collection<Long>, List<Object[]>> countQuery) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> totals = new HashMap<>();
        for (Object[] row : countQuery.apply(ids)) {
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

    /** Entity-in: called by AnalysisResultPersistenceService within the same transaction it loaded the run in. */
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
