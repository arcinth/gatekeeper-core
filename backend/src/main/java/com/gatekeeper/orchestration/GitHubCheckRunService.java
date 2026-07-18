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
import com.gatekeeper.verdict.Verdict;
import com.gatekeeper.verdict.VerdictReasonEntity;
import com.gatekeeper.verdict.VerdictReasonRepository;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdictengine.VerdictOutcome;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes GateKeeper's Verdict onto the pull request as a GitHub Check Run
 * - Sprint 2's write-back counterpart to ReportPublicationService, and
 * deliberately shaped the same way: one idempotent entry point
 * ({@link #publishForVerdict}) triggered by VerdictProducedEvent, reading the
 * already-committed Verdict rather than computing anything itself, and never
 * touching AnalysisRun's own lifecycle status or the Verdict/VerdictReason
 * rows it reads.
 * <p>
 * Create-vs-update is decided by whether {@code AnalysisRun.githubCheckRunId}
 * is already set (see that field's own Javadoc): GitHub has no "find the
 * check run for this commit" lookup, so the id must be remembered by whoever
 * creates it. Under normal operation this method runs exactly once per
 * analysis run (VerdictProducedEvent is published exactly once, from
 * AnalysisResultPersistenceService's single COMPLETED transition), so the
 * update path mainly exists for resilience against a future retry path
 * (e.g. a timeout-sweep-style catch-up job, mirroring ReportTimeoutSweepJob)
 * rather than a race this class itself needs to resolve.
 * <p>
 * Injects AnalysisRunRepository directly for the one write this class owns
 * (recording githubCheckRunId) rather than adding a method to
 * AnalysisRunService, the same way ReportPublicationService injects
 * EngineeringReportRepository/AuditLogRepository directly instead of routing
 * through another service - AnalysisRunService itself is left untouched.
 */
@Slf4j
@Service
public class GitHubCheckRunService {

    private static final String STATUS_COMPLETED = "completed";
    private static final String CONCLUSION_SUCCESS = "success";
    private static final String CONCLUSION_FAILURE = "failure";

    private final String checkRunName;
    private final AnalysisRunService analysisRunService;
    private final AnalysisRunRepository analysisRunRepository;
    private final VerdictRepository verdictRepository;
    private final VerdictReasonRepository verdictReasonRepository;
    private final GitHubAppAuthService gitHubAppAuthService;
    private final GitHubApiClient gitHubApiClient;
    private final Clock clock;
    // TEMPORARY DIAGNOSTIC - remove once the check-run publication gap is resolved.
    private final EntityManager entityManager;

    public GitHubCheckRunService(
            @Value("${gatekeeper.github.check-run.name}") String checkRunName,
            AnalysisRunService analysisRunService,
            AnalysisRunRepository analysisRunRepository,
            VerdictRepository verdictRepository,
            VerdictReasonRepository verdictReasonRepository,
            GitHubAppAuthService gitHubAppAuthService,
            GitHubApiClient gitHubApiClient,
            Clock clock,
            EntityManager entityManager) {
        this.checkRunName = checkRunName;
        this.analysisRunService = analysisRunService;
        this.analysisRunRepository = analysisRunRepository;
        this.verdictRepository = verdictRepository;
        this.verdictReasonRepository = verdictReasonRepository;
        this.gitHubAppAuthService = gitHubAppAuthService;
        this.gitHubApiClient = gitHubApiClient;
        this.clock = clock;
        this.entityManager = entityManager;
    }

    @Transactional
    public void publishForVerdict(Long analysisRunId) {
        // TEMPORARY DIAGNOSTIC - remove once the check-run publication gap is resolved.
        log.info("DIAGNOSTIC: publishForVerdict entered for analysis run {}.", analysisRunId);
        AnalysisRun analysisRun = analysisRunService.findWithPullRequestAndRepositoryByIdOrThrow(analysisRunId);
        Repository repository = analysisRun.getPullRequest().getRepository();
        if (repository.getGithubInstallation() == null) {
            log.info("DIAGNOSTIC: Analysis run {} has no linked GitHub installation; skipping check run publication.",
                    analysisRunId);
            return;
        }

        Verdict verdict = verdictRepository.findByAnalysisRunId(analysisRunId).orElse(null);
        if (verdict == null) {
            log.warn("DIAGNOSTIC: No Verdict found for analysis run {} when publishing its check run; skipping.",
                    analysisRunId);
            return;
        }
        // TEMPORARY DIAGNOSTIC - remove once the check-run publication gap is resolved.
        log.info("DIAGNOSTIC: Analysis run {} passed both skip checks (installation={}, verdict={}); "
                        + "proceeding to call GitHub.",
                analysisRunId, repository.getGithubInstallation().getInstallationId(), verdict.getOutcome());

        String conclusion = verdict.getOutcome() == VerdictOutcome.APPROVED ? CONCLUSION_SUCCESS : CONCLUSION_FAILURE;
        CheckRunOutput output = buildOutput(verdict);
        long installationId = repository.getGithubInstallation().getInstallationId();
        // TEMPORARY DIAGNOSTIC - remove once the check-run publication gap is resolved.
        log.info("DIAGNOSTIC: requesting installation access token for installation {} (analysis run {}).",
                installationId, analysisRun.getId());
        String installationAccessToken = gitHubAppAuthService.getInstallationAccessToken(installationId);
        // TEMPORARY DIAGNOSTIC - remove once the check-run publication gap is resolved.
        log.info("DIAGNOSTIC: obtained installation access token for analysis run {}.", analysisRun.getId());
        Instant now = clock.instant();

        if (analysisRun.getGithubCheckRunId() == null) {
            create(analysisRun, repository, conclusion, output, installationAccessToken, now);
        } else {
            update(analysisRun, repository, conclusion, output, installationAccessToken, now);
        }
    }

    private void create(AnalysisRun analysisRun, Repository repository, String conclusion, CheckRunOutput output,
            String installationAccessToken, Instant now) {
        CreateCheckRunRequest request = new CreateCheckRunRequest(
                checkRunName, analysisRun.getCommitSha(), STATUS_COMPLETED, conclusion, now, now, output);
        // TEMPORARY DIAGNOSTIC - remove once the check-run publication gap is resolved.
        log.info("DIAGNOSTIC: calling gitHubApiClient.createCheckRun for repository '{}' (analysis run {}).",
                repository.getFullName(), analysisRun.getId());
        CheckRunResponse response = gitHubApiClient.createCheckRun(
                repository.getFullName(), request, installationAccessToken);
        // TEMPORARY DIAGNOSTIC - remove once the check-run publication gap is resolved.
        log.info("DIAGNOSTIC: response.id() = {} (analysis run {}).", response.id(), analysisRun.getId());

        // TEMPORARY DIAGNOSTIC - remove once the check-run publication gap is resolved.
        // Checked before mutating anything: if the session is already in a
        // read-only state (or this specific entity is), Hibernate's dirty
        // checking silently skips generating an UPDATE for it at flush time -
        // setGithubCheckRunId below would succeed, save() would succeed, and
        // commit would succeed, all with no exception, yet nothing would reach
        // Postgres. This is the one mechanism that fits every fact gathered so
        // far: HTTP call succeeds, no exception anywhere, value never persists.
        Session hibernateSession = entityManager.unwrap(Session.class);
        log.info("DIAGNOSTIC: session.isDefaultReadOnly()={}, session.getHibernateFlushMode()={}, "
                        + "session.isReadOnly(analysisRun)={} (analysis run {}).",
                hibernateSession.isDefaultReadOnly(), hibernateSession.getHibernateFlushMode(),
                hibernateSession.isReadOnly(analysisRun), analysisRun.getId());

        analysisRun.setGithubCheckRunId(response.id());
        // TEMPORARY DIAGNOSTIC - remove once the check-run publication gap is resolved.
        log.info("DIAGNOSTIC: after setGithubCheckRunId, analysisRun.getGithubCheckRunId() = {} (analysis run {}).",
                analysisRun.getGithubCheckRunId(), analysisRun.getId());

        AnalysisRun saved = analysisRunRepository.save(analysisRun);
        // TEMPORARY DIAGNOSTIC - remove once the check-run publication gap is resolved.
        log.info("DIAGNOSTIC: after save(), saved.getGithubCheckRunId() = {}, saved == analysisRun: {} "
                        + "(analysis run {}).",
                saved.getGithubCheckRunId(), saved == analysisRun, analysisRun.getId());

        // TEMPORARY DIAGNOSTIC - remove once the check-run publication gap is resolved.
        // flush() forces Hibernate to issue the UPDATE now, inside this same
        // transaction/connection, rather than waiting for commit. clear() then
        // evicts everything from the persistence context so the reload below
        // issues a genuine SELECT instead of returning the same cached instance
        // from the first-level cache - only a real SELECT proves the value is
        // actually visible in Postgres (via this transaction's own connection,
        // which sees its own uncommitted writes) rather than just proving the
        // in-memory Java object was mutated.
        Long analysisRunId = analysisRun.getId();
        analysisRunRepository.flush();
        entityManager.clear();
        Long reloadedCheckRunId = analysisRunRepository.findById(analysisRunId)
                .map(AnalysisRun::getGithubCheckRunId)
                .orElse(null);
        log.info("DIAGNOSTIC: after flush()+clear()+reload by id, githubCheckRunId visible in DB "
                        + "(same transaction) = {} (analysis run {}).",
                reloadedCheckRunId, analysisRunId);

        log.info("Created GitHub check run {} for analysis run {} (conclusion={}).",
                response.id(), analysisRunId, conclusion);
    }

    private void update(AnalysisRun analysisRun, Repository repository, String conclusion, CheckRunOutput output,
            String installationAccessToken, Instant now) {
        UpdateCheckRunRequest request = new UpdateCheckRunRequest(STATUS_COMPLETED, conclusion, now, output);
        gitHubApiClient.updateCheckRun(
                repository.getFullName(), analysisRun.getGithubCheckRunId(), request, installationAccessToken);
        log.info("Updated GitHub check run {} for analysis run {} (conclusion={}).",
                analysisRun.getGithubCheckRunId(), analysisRun.getId(), conclusion);
    }

    private CheckRunOutput buildOutput(Verdict verdict) {
        List<VerdictReasonEntity> reasons = verdictReasonRepository.findByVerdictIdOrderById(verdict.getId());
        String title = "Verdict: " + verdict.getOutcome();
        String summary = reasons.isEmpty()
                ? "No policy or security findings were recorded."
                : reasons.stream()
                        .map(reason -> "- **" + reason.getRuleId() + "**"
                                + (reason.isBlocking() ? " (blocking)" : " (informational)")
                                + ": " + reason.getMessage())
                        .collect(Collectors.joining("\n"));
        return new CheckRunOutput(title, summary);
    }
}
