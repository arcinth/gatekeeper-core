package com.gatekeeper.pullrequest;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.pullrequest.dto.AnalysisRunReference;
import com.gatekeeper.pullrequest.dto.PullRequestDetailResponse;
import com.gatekeeper.pullrequest.dto.PullRequestFilter;
import com.gatekeeper.pullrequest.dto.PullRequestSummaryResponse;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.verdict.Verdict;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists Pull Requests received from GitHub. Upserts by githubPrId rather
 * than exposing separate create/update methods: a webhook redelivery for a PR
 * GateKeeper already knows about must update it, not fail or duplicate it
 * (Sprint 2 Architecture, Section 8: Pull Request Lifecycle).
 * <p>
 * Milestone 1 (Pull Requests as the reviewer's primary workspace) adds the
 * read side (findSummaryPage/findDetailByIdOrThrow) to this same class rather
 * than a parallel query service, mirroring AnalysisRunService's own
 * established mixing of read and write methods under one transactional
 * policy.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PullRequestService {

    private final PullRequestRepository pullRequestRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final VerdictRepository verdictRepository;

    @Transactional
    public PullRequest upsert(Repository repository, PullRequestUpsertCommand command) {
        return pullRequestRepository.findByGithubPrId(command.githubPrId())
                .map(existing -> applyUpdate(existing, command))
                .orElseGet(() -> insertOrRecoverFromRace(repository, command));
    }

    /**
     * Handles the case where a concurrent webhook redelivery for the same PR
     * inserts first: the unique constraint on github_pr_id turns our insert
     * into a DataIntegrityViolationException instead of a duplicate row, and
     * that loss is not a real failure - the PR is correctly persisted either way.
     */
    private PullRequest insertOrRecoverFromRace(Repository repository, PullRequestUpsertCommand command) {
        try {
            return pullRequestRepository.saveAndFlush(buildNew(repository, command));
        } catch (DataIntegrityViolationException ex) {
            return pullRequestRepository.findByGithubPrId(command.githubPrId())
                    .map(existing -> applyUpdate(existing, command))
                    .orElseThrow(() -> ex);
        }
    }

    private PullRequest applyUpdate(PullRequest pullRequest, PullRequestUpsertCommand command) {
        pullRequest.setTitle(command.title());
        pullRequest.setHeadSha(command.headSha());
        pullRequest.setStatus(resolveStatus(command));
        return pullRequestRepository.save(pullRequest);
    }

    private PullRequest buildNew(Repository repository, PullRequestUpsertCommand command) {
        return PullRequest.builder()
                .repository(repository)
                .githubPrId(command.githubPrId())
                .number(command.number())
                .title(command.title())
                .authorLogin(command.authorLogin())
                .sourceBranch(command.sourceBranch())
                .targetBranch(command.targetBranch())
                .headSha(command.headSha())
                .status(resolveStatus(command))
                .build();
    }

    private PullRequestStatus resolveStatus(PullRequestUpsertCommand command) {
        if (command.merged()) {
            return PullRequestStatus.MERGED;
        }
        return "closed".equals(command.githubState()) ? PullRequestStatus.CLOSED : PullRequestStatus.OPEN;
    }

    /**
     * List-view row per Pull Request, enriched with its most recent
     * AnalysisRun's status and verdict outcome via two batched supplementary
     * queries over the page's ids - the same shape
     * AnalysisRunService.findSummaryPage already established for its own
     * findingsTotal/verdictOutcome enrichment, not a new pattern.
     */
    public Page<PullRequestSummaryResponse> findSummaryPage(PullRequestFilter filter, Pageable pageable) {
        Page<PullRequest> page = pullRequestRepository.findAll(PullRequestSpecifications.matching(filter), pageable);
        List<Long> pullRequestIds = page.getContent().stream().map(PullRequest::getId).toList();
        Map<Long, AnalysisRun> latestRunByPullRequestId = latestAnalysisRunByPullRequestId(pullRequestIds);
        Map<Long, VerdictOutcome> verdictOutcomesByRunId = verdictOutcomesByAnalysisRunId(
                latestRunByPullRequestId.values().stream().map(AnalysisRun::getId).toList());
        return page.map(pullRequest -> {
            AnalysisRun latestRun = latestRunByPullRequestId.get(pullRequest.getId());
            VerdictOutcome verdictOutcome = latestRun == null ? null : verdictOutcomesByRunId.get(latestRun.getId());
            return PullRequestSummaryResponse.from(pullRequest, latestRun, verdictOutcome);
        });
    }

    public PullRequestDetailResponse findDetailByIdOrThrow(Long id) {
        PullRequest pullRequest = pullRequestRepository.findWithRepositoryById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Pull request not found with id: " + id));
        List<AnalysisRun> runs = analysisRunRepository.findByPullRequestIdOrderByCreatedAtDesc(id);
        Map<Long, VerdictOutcome> verdictOutcomes =
                verdictOutcomesByAnalysisRunId(runs.stream().map(AnalysisRun::getId).toList());
        List<AnalysisRunReference> analysisRuns = runs.stream()
                .map(run -> AnalysisRunReference.from(run, verdictOutcomes.get(run.getId())))
                .toList();
        return PullRequestDetailResponse.from(pullRequest, analysisRuns);
    }

    /**
     * Keeps only the first AnalysisRun per pullRequestId - the query is
     * already ordered pullRequestId ASC, createdAt DESC within each group, so
     * the first occurrence per key is the most recent run. A single query
     * plus an in-memory reduction, rather than a native "latest per group"
     * query.
     */
    private Map<Long, AnalysisRun> latestAnalysisRunByPullRequestId(List<Long> pullRequestIds) {
        if (pullRequestIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, AnalysisRun> latestByPullRequestId = new HashMap<>();
        for (AnalysisRun run : analysisRunRepository.findByPullRequestIdInOrderByPullRequestIdAscCreatedAtDesc(pullRequestIds)) {
            latestByPullRequestId.putIfAbsent(run.getPullRequest().getId(), run);
        }
        return latestByPullRequestId;
    }

    /** Mirrors AnalysisRunService.verdictOutcomesByAnalysisRunId's own empty-list guard and batching shape. */
    private Map<Long, VerdictOutcome> verdictOutcomesByAnalysisRunId(List<Long> analysisRunIds) {
        if (analysisRunIds.isEmpty()) {
            return Map.of();
        }
        return verdictRepository.findByAnalysisRunIdIn(analysisRunIds).stream()
                .collect(Collectors.toMap(verdict -> verdict.getAnalysisRun().getId(), Verdict::getOutcome));
    }
}
