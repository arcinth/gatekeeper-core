package com.gatekeeper.orchestration;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunService;
import com.gatekeeper.github.GitHubAppAuthService;
import com.gatekeeper.github.GitHubApiClient;
import com.gatekeeper.github.dto.CheckRunOutput;
import com.gatekeeper.github.dto.CheckRunResponse;
import com.gatekeeper.github.dto.CreateCheckRunRequest;
import com.gatekeeper.github.dto.UpdateCheckRunRequest;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.reviewdecision.ReviewDecision;
import com.gatekeeper.reviewdecision.ReviewDecisionRepository;
import java.time.Clock;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes a reviewer's decision onto the pull request as a GitHub Check Run
 * named "GateKeeper Review" (Milestone 4) - a deliberately separate check
 * from the Verdict-driven one {@link GitHubCheckRunService} publishes.
 * Analysis and human review are two independent sources of truth (per
 * product direction, and consistent with {@code API-Design.md}'s "merge
 * decisions originate only from deterministic engines"): this class never
 * reads, writes, or otherwise touches {@code AnalysisRun.githubCheckRunId},
 * {@code Verdict}, or {@link GitHubCheckRunService} - it owns exactly one
 * column, {@code AnalysisRun.githubReviewCheckRunId}, and exactly one GitHub
 * check run per analysis run.
 * <p>
 * Shaped the same way as {@code GitHubCheckRunService}: one idempotent entry
 * point ({@link #publishForReviewDecision}) triggered by an event
 * (here, {@link com.gatekeeper.reviewdecision.ReviewDecisionRecordedEvent}),
 * reading the already-committed {@link ReviewDecision} rather than computing
 * anything itself. Create-vs-update is decided by whether
 * {@code githubReviewCheckRunId} is already set - GitHub has no "find the
 * check run for this commit" lookup, so the id must be remembered by whoever
 * creates it.
 * <p>
 * The check always reflects the <em>latest</em> decision for the run (see
 * {@link ReviewDecisionRepository#findFirstByAnalysisRunIdOrderByCreatedAtDesc}) -
 * a reviewer changing their mind creates a new, additional {@code ReviewDecision}
 * row (the history stays append-only and is never mutated), and this method
 * simply re-publishes the now-latest one to the same check run.
 * <p>
 * {@code publishForReviewDecision} being {@code @Transactional} only works
 * correctly because its caller
 * ({@link GitHubReviewDecisionCheckRunPublisher#onReviewDecisionRecorded})
 * is {@code @Async} - see that method's Javadoc, which mirrors
 * {@link GitHubCheckRunPublisher#onVerdictProduced}'s own reasoning exactly.
 */
@Slf4j
@Service
public class GitHubReviewDecisionCheckRunService {

    private static final String STATUS_COMPLETED = "completed";

    private final String checkRunName;
    private final AnalysisRunService analysisRunService;
    private final AnalysisRunRepository analysisRunRepository;
    private final ReviewDecisionRepository reviewDecisionRepository;
    private final ReviewDecisionConclusionMapper conclusionMapper;
    private final GitHubAppAuthService gitHubAppAuthService;
    private final GitHubApiClient gitHubApiClient;
    private final Clock clock;

    public GitHubReviewDecisionCheckRunService(
            @Value("${gatekeeper.github.check-run.review-decision-name}") String checkRunName,
            AnalysisRunService analysisRunService,
            AnalysisRunRepository analysisRunRepository,
            ReviewDecisionRepository reviewDecisionRepository,
            ReviewDecisionConclusionMapper conclusionMapper,
            GitHubAppAuthService gitHubAppAuthService,
            GitHubApiClient gitHubApiClient,
            Clock clock) {
        this.checkRunName = checkRunName;
        this.analysisRunService = analysisRunService;
        this.analysisRunRepository = analysisRunRepository;
        this.reviewDecisionRepository = reviewDecisionRepository;
        this.conclusionMapper = conclusionMapper;
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.gitHubApiClient = gitHubApiClient;
        this.clock = clock;
    }

    @Transactional
    public void publishForReviewDecision(Long analysisRunId) {
        AnalysisRun analysisRun = analysisRunService.findWithPullRequestAndRepositoryByIdOrThrow(analysisRunId);
        Repository repository = analysisRun.getPullRequest().getRepository();
        if (repository.getGithubInstallation() == null) {
            log.info("Analysis run {} has no linked GitHub installation; skipping review check run publication.",
                    analysisRunId);
            return;
        }

        ReviewDecision latestDecision = reviewDecisionRepository
                .findFirstByAnalysisRunIdOrderByCreatedAtDesc(analysisRunId)
                .orElse(null);
        if (latestDecision == null) {
            log.warn("No ReviewDecision found for analysis run {} when publishing its review check run; skipping.",
                    analysisRunId);
            return;
        }

        String conclusion = conclusionMapper.toConclusion(latestDecision.getDecision());
        CheckRunOutput output = buildOutput(latestDecision);
        long installationId = repository.getGithubInstallation().getInstallationId();
        String installationAccessToken = gitHubAppAuthService.getInstallationAccessToken(installationId);
        Instant now = clock.instant();

        if (analysisRun.getGithubReviewCheckRunId() == null) {
            create(analysisRun, repository, conclusion, output, installationAccessToken, now);
        } else {
            update(analysisRun, repository, conclusion, output, installationAccessToken, now);
        }
    }

    private void create(AnalysisRun analysisRun, Repository repository, String conclusion, CheckRunOutput output,
            String installationAccessToken, Instant now) {
        CreateCheckRunRequest request = new CreateCheckRunRequest(
                checkRunName, analysisRun.getCommitSha(), STATUS_COMPLETED, conclusion, now, now, output);
        CheckRunResponse response = gitHubApiClient.createCheckRun(
                repository.getFullName(), request, installationAccessToken);

        analysisRun.setGithubReviewCheckRunId(response.id());
        analysisRunRepository.save(analysisRun);
        log.info("Created GitHub review check run {} for analysis run {} (conclusion={}).",
                response.id(), analysisRun.getId(), conclusion);
    }

    private void update(AnalysisRun analysisRun, Repository repository, String conclusion, CheckRunOutput output,
            String installationAccessToken, Instant now) {
        UpdateCheckRunRequest request = new UpdateCheckRunRequest(STATUS_COMPLETED, conclusion, now, output);
        gitHubApiClient.updateCheckRun(
                repository.getFullName(), analysisRun.getGithubReviewCheckRunId(), request, installationAccessToken);
        log.info("Updated GitHub review check run {} for analysis run {} (conclusion={}).",
                analysisRun.getGithubReviewCheckRunId(), analysisRun.getId(), conclusion);
    }

    /** Title/summary deliberately name the reviewer, decision, optional comment, and timestamp - all four required per Milestone 4's own acceptance criteria. */
    private CheckRunOutput buildOutput(ReviewDecision decision) {
        String title = decision.getDecision() + " by " + decision.getReviewer().getFullName();
        StringBuilder summary = new StringBuilder()
                .append("**Reviewer:** ").append(decision.getReviewer().getFullName()).append('\n')
                .append("**Decision:** ").append(decision.getDecision()).append('\n');
        if (decision.getComment() != null && !decision.getComment().isBlank()) {
            summary.append("**Comment:** ").append(decision.getComment()).append('\n');
        }
        summary.append("**Decided at:** ").append(decision.getCreatedAt());
        return new CheckRunOutput(title, summary.toString());
    }
}
