package com.gatekeeper.demo;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewfinding.AIReviewFindingEntity;
import com.gatekeeper.aireviewfinding.AIReviewFindingRepository;
import com.gatekeeper.aireviewrun.AIReviewRun;
import com.gatekeeper.aireviewrun.AIReviewRunRepository;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.github.GitHubInstallation;
import com.gatekeeper.github.GitHubInstallationRepository;
import com.gatekeeper.github.GitHubInstallationStatus;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.organization.OrganizationService;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.PolicyFindingEntity;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.pullrequest.PullRequestRepository;
import com.gatekeeper.pullrequest.PullRequestStatus;
import com.gatekeeper.report.AiReviewStatus;
import com.gatekeeper.report.EngineeringReport;
import com.gatekeeper.report.EngineeringReportRepository;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.repository.RepositoryRepository;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.SecurityFindingEntity;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import com.gatekeeper.verdict.Verdict;
import com.gatekeeper.verdict.VerdictReasonEntity;
import com.gatekeeper.verdict.VerdictReasonRepository;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * A small, deliberately-authored demo world (Product Experience spec, §12).
 *
 * The product previously had no seeder at all - the only auto-created row was
 * the bootstrap admin - so any demo data was whatever ad hoc testing happened
 * to leave behind, which is why it read as noisy and unbelievable. This seeds
 * the opposite: five repositories that each demonstrate exactly one thing,
 * roughly a dozen pull requests, and findings that trace to plausible files
 * and line numbers.
 *
 * Quality over quantity is the entire point. Twenty pull requests that each
 * mean something beat a thousand that mean nothing: a salesperson can open
 * payments-api and say "watch what happens", which is what a demo is for.
 *
 * Strictly opt-in via {@code SPRING_PROFILES_ACTIVE=demo} and idempotent - it
 * refuses to run if any repository already exists, so it can never scribble on
 * a real deployment or double-seed a restart.
 */
@Slf4j
@Component
@Profile("demo")
@RequiredArgsConstructor
public class DemoDataSeeder implements ApplicationRunner {

    private static final String OWNER = "northwind";

    private final OrganizationService organizationService;
    private final GitHubInstallationRepository gitHubInstallationRepository;
    private final RepositoryRepository repositoryRepository;
    private final PullRequestRepository pullRequestRepository;
    private final AnalysisRunRepository analysisRunRepository;
    private final PolicyFindingRepository policyFindingRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final AIReviewRunRepository aiReviewRunRepository;
    private final AIReviewFindingRepository aiReviewFindingRepository;
    private final VerdictRepository verdictRepository;
    private final VerdictReasonRepository verdictReasonRepository;
    private final EngineeringReportRepository engineeringReportRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repositoryRepository.count() > 0) {
            log.info("Demo seed skipped: this database already contains repositories.");
            return;
        }

        Organization organization = organizationService.getDefaultOrganization();
        GitHubInstallation installation = seedInstallation(organization);

        seedPaymentsApi(organization, installation);
        seedWebDashboard(organization, installation);
        seedInfraTerraform(organization, installation);
        seedInternalSdk(organization, installation);
        seedLegacyMonolith(organization, installation);

        log.info("Demo seed complete: {} repositories, {} pull requests.",
                repositoryRepository.count(), pullRequestRepository.count());
    }

    private GitHubInstallation seedInstallation(Organization organization) {
        return gitHubInstallationRepository.save(GitHubInstallation.builder()
                .organization(organization)
                .installationId(48211907L)
                .githubAccountLogin(OWNER)
                .githubAccountId(9214477L)
                .githubAccountType("Organization")
                .repositorySelection("selected")
                .active(true)
                .status(GitHubInstallationStatus.ACTIVE)
                .lastSuccessfulSyncAt(Instant.now().minus(Duration.ofMinutes(18)))
                .build());
    }

    /** The high-stakes service: shows GateKeeper catching something that genuinely matters. */
    private void seedPaymentsApi(Organization organization, GitHubInstallation installation) {
        Repository repository = repository(organization, installation, "payments-api", 411002931L,
                "Card authorization and settlement service.");

        AnalysisRun blockedRun = analysisRun(
                pullRequest(repository, 4821, 318, "Add Stripe webhook retry handling", "amara-osei",
                        "feat/stripe-retries", Duration.ofHours(3)),
                "9f2c4ae7d1b8", AnalysisRunTriggerReason.SYNCHRONIZE);

        securityFinding(blockedRun, "AWS_ACCESS_KEY", SecurityCategory.SECRETS_EXPOSURE, SecuritySeverity.CRITICAL,
                "config/prod.yml", 12,
                "AWS access key ID committed to source control.",
                "Revoke this key in IAM immediately, then move it to the deployment secret store. Rotating is not optional - assume it is already compromised.");
        securityFinding(blockedRun, "HARDCODED_SECRET", SecurityCategory.SECRETS_EXPOSURE, SecuritySeverity.HIGH,
                "src/main/java/com/northwind/payments/StripeClient.java", 47,
                "Hardcoded API secret passed to StripeClient.",
                "Inject the secret from configuration rather than embedding it in the constructor.");
        policyFinding(blockedRun, "TODO_COMMENT", PolicyCategory.MAINTAINABILITY, PolicySeverity.LOW,
                "src/main/java/com/northwind/payments/RetryPolicy.java", 88,
                "TODO comment left in a production code path.",
                "Resolve the TODO or convert it into a tracked issue before merging.");

        verdict(blockedRun, VerdictOutcome.BLOCKED,
                new String[][] {
                    {"CRITICAL_SECURITY_FINDING", "true",
                        "1 critical security finding must be resolved before this pull request can merge."},
                    {"HIGH_SEVERITY_POLICY_FINDING", "false",
                        "1 high-severity security finding was recorded but does not block on its own."},
                });
        report(blockedRun, AiReviewStatus.INCLUDED);
        aiReview(blockedRun, "Retry handling looks correct. Two smaller observations noted inline.",
                new Object[][] {
                    {AIReviewFindingType.POTENTIAL_BUG, AIReviewConfidence.MEDIUM,
                        "src/main/java/com/northwind/payments/RetryPolicy.java", 64,
                        "Exponential backoff has no jitter, which can synchronize retries across instances.",
                        "Add randomized jitter to spread retry attempts."},
                });

        // A second, already-merged pull request so the repository has history.
        AnalysisRun mergedRun = analysisRun(
                mergedPullRequest(repository, 4790, 312, "Extract settlement reconciliation into its own service",
                        "daniel-reyes", "refactor/settlement-service", Duration.ofDays(4)),
                "1a77be03c9f4", AnalysisRunTriggerReason.OPENED);
        verdict(mergedRun, VerdictOutcome.APPROVED, new String[0][]);
        report(mergedRun, AiReviewStatus.INCLUDED);
    }

    /** The healthy everyday repository: shows the calm, passing steady state. */
    private void seedWebDashboard(Organization organization, GitHubInstallation installation) {
        Repository repository = repository(organization, installation, "web-dashboard", 411004120L,
                "Customer-facing React dashboard.");

        AnalysisRun run = analysisRun(
                pullRequest(repository, 2210, 947, "Virtualize the transactions table", "priya-raman",
                        "perf/virtualized-table", Duration.ofHours(9)),
                "5c81de42a30f", AnalysisRunTriggerReason.OPENED);

        verdict(run, VerdictOutcome.APPROVED, new String[0][]);
        report(run, AiReviewStatus.INCLUDED);
        aiReview(run, "Clean change. One readability suggestion, nothing blocking.",
                new Object[][] {
                    {AIReviewFindingType.CLARITY, AIReviewConfidence.LOW,
                        "src/components/TransactionsTable.tsx", 132,
                        "The row-height constant is repeated in three places.",
                        "Extract it to a single named constant so the virtualizer and the styles cannot drift."},
                });
    }

    /** Policy in action: organization standards enforced on infrastructure-as-code. */
    private void seedInfraTerraform(Organization organization, GitHubInstallation installation) {
        Repository repository = repository(organization, installation, "infra-terraform", 411006644L,
                "Terraform modules for all Northwind environments.");

        AnalysisRun run = analysisRun(
                pullRequest(repository, 1580, 204, "Add archival S3 bucket for settlement exports", "tom-whitfield",
                        "infra/settlement-archive", Duration.ofHours(26)),
                "b409f7c1e8a2", AnalysisRunTriggerReason.SYNCHRONIZE);

        policyFinding(run, "ENCRYPTION_REQUIRED", PolicyCategory.CODE_QUALITY, PolicySeverity.HIGH,
                "modules/storage/s3.tf", 34,
                "S3 bucket declared without a server-side encryption block.",
                "Add a server_side_encryption_configuration block. Northwind policy requires encryption at rest for every bucket.");
        policyFinding(run, "TODO_COMMENT", PolicyCategory.MAINTAINABILITY, PolicySeverity.LOW,
                "modules/storage/s3.tf", 61,
                "TODO comment left in a production module.",
                "Resolve the TODO or track it as an issue.");

        verdict(run, VerdictOutcome.BLOCKED,
                new String[][] {
                    {"HIGH_SEVERITY_POLICY_FINDING", "true",
                        "1 high-severity policy finding must be resolved before this pull request can merge."},
                });
        report(run, AiReviewStatus.DISABLED);
    }

    /** The low-risk library: mostly green, with the kind of noise that motivates tuning. */
    private void seedInternalSdk(Organization organization, GitHubInstallation installation) {
        Repository repository = repository(organization, installation, "internal-sdk", 411009087L,
                "Shared Java client for internal services.");

        AnalysisRun run = analysisRun(
                pullRequest(repository, 903, 77, "Support pagination cursors in the client", "amara-osei",
                        "feat/cursor-pagination", Duration.ofDays(2)),
                "7ea0cc95b164", AnalysisRunTriggerReason.OPENED);

        policyFinding(run, "TODO_COMMENT", PolicyCategory.MAINTAINABILITY, PolicySeverity.LOW,
                "src/main/java/com/northwind/sdk/PageCursor.java", 41,
                "TODO comment left in a public API surface.",
                "Resolve before release, or downgrade this rule for library code if it is not useful here.");

        verdict(run, VerdictOutcome.APPROVED, new String[0][]);
        report(run, AiReviewStatus.INCLUDED);
    }

    /** The inherited mess: shows GateKeeper managing risk without drowning the team. */
    private void seedLegacyMonolith(Organization organization, GitHubInstallation installation) {
        Repository repository = repository(organization, installation, "legacy-monolith", 411001002L,
                "The original Northwind platform. Maintenance only.");

        AnalysisRun run = analysisRun(
                pullRequest(repository, 6604, 1521, "Patch session fixation in the legacy login filter", "daniel-reyes",
                        "fix/session-fixation", Duration.ofHours(52)),
                "c2f5a8017bd3", AnalysisRunTriggerReason.SYNCHRONIZE);

        securityFinding(run, "INSECURE_CRYPTO_FUNCTION", SecurityCategory.INSECURE_CRYPTOGRAPHY, SecuritySeverity.HIGH,
                "src/main/java/com/northwind/legacy/auth/LegacyDigest.java", 29,
                "MD5 used to derive a session token.",
                "Replace MD5 with SHA-256 and a per-session salt, or move to the platform session service.");
        securityFinding(run, "INSECURE_RANDOMNESS", SecurityCategory.INSECURE_CRYPTOGRAPHY, SecuritySeverity.MEDIUM,
                "src/main/java/com/northwind/legacy/auth/LegacyDigest.java", 44,
                "java.util.Random used to generate a security-sensitive value.",
                "Use java.security.SecureRandom for anything reaching an authentication path.");
        policyFinding(run, "FIXME_COMMENT", PolicyCategory.MAINTAINABILITY, PolicySeverity.MEDIUM,
                "src/main/java/com/northwind/legacy/auth/LoginFilter.java", 210,
                "FIXME comment in an authentication filter.",
                "Authentication code should not ship with unresolved FIXMEs - resolve or track it.");

        verdict(run, VerdictOutcome.BLOCKED,
                new String[][] {
                    {"HIGH_SEVERITY_POLICY_FINDING", "true",
                        "1 high-severity security finding must be resolved before this pull request can merge."},
                });
        report(run, AiReviewStatus.UNAVAILABLE);

        // A failed run, so the pipeline-health surfaces have something honest to show.
        AnalysisRun failed = analysisRunRepository.save(AnalysisRun.builder()
                .pullRequest(pullRequest(repository, 6598, 1519, "Bump legacy Struts dependency", "tom-whitfield",
                        "chore/struts-bump", Duration.ofDays(6)))
                .commitSha("48be1f0d7c25")
                .triggerReason(AnalysisRunTriggerReason.OPENED)
                .status(AnalysisRunStatus.FAILED)
                .failureReason("Timed out fetching changed files from GitHub after 3 attempts.")
                .build());
        log.debug("Seeded failed analysis run {}", failed.getId());
    }

    // ---------------------------------------------------------------- helpers

    private Repository repository(Organization organization, GitHubInstallation installation, String name,
            long githubRepositoryId, String description) {
        return repositoryRepository.save(Repository.builder()
                .organization(organization)
                .githubInstallation(installation)
                .name(name)
                .owner(OWNER)
                .fullName(OWNER + "/" + name)
                .description(description)
                .githubRepositoryId(githubRepositoryId)
                .defaultBranch("main")
                .active(true)
                .build());
    }

    private PullRequest pullRequest(Repository repository, long githubPrId, int number, String title,
            String author, String sourceBranch, Duration age) {
        return savePullRequest(repository, githubPrId, number, title, author, sourceBranch, age,
                PullRequestStatus.OPEN);
    }

    private PullRequest mergedPullRequest(Repository repository, long githubPrId, int number, String title,
            String author, String sourceBranch, Duration age) {
        return savePullRequest(repository, githubPrId, number, title, author, sourceBranch, age,
                PullRequestStatus.MERGED);
    }

    private PullRequest savePullRequest(Repository repository, long githubPrId, int number, String title,
            String author, String sourceBranch, Duration age, PullRequestStatus status) {
        return pullRequestRepository.save(PullRequest.builder()
                .repository(repository)
                .githubPrId(githubPrId)
                .number(number)
                .title(title)
                .authorLogin(author)
                .sourceBranch(sourceBranch)
                .targetBranch("main")
                .headSha(Long.toHexString(githubPrId * 7919L))
                .status(status)
                .build());
    }

    private AnalysisRun analysisRun(PullRequest pullRequest, String commitSha, AnalysisRunTriggerReason reason) {
        return analysisRunRepository.save(AnalysisRun.builder()
                .pullRequest(pullRequest)
                .commitSha(commitSha)
                .triggerReason(reason)
                .status(AnalysisRunStatus.COMPLETED)
                .build());
    }

    private void policyFinding(AnalysisRun run, String ruleId, PolicyCategory category, PolicySeverity severity,
            String filePath, int lineNumber, String message, String recommendation) {
        policyFindingRepository.save(PolicyFindingEntity.builder()
                .analysisRun(run)
                .ruleId(ruleId)
                .category(category)
                .severity(severity)
                .filePath(filePath)
                .lineNumber(lineNumber)
                .message(message)
                .recommendation(recommendation)
                .createdAt(Instant.now())
                .build());
    }

    private void securityFinding(AnalysisRun run, String ruleId, SecurityCategory category, SecuritySeverity severity,
            String filePath, int lineNumber, String message, String recommendation) {
        securityFindingRepository.save(SecurityFindingEntity.builder()
                .analysisRun(run)
                .ruleId(ruleId)
                .category(category)
                .severity(severity)
                .filePath(filePath)
                .lineNumber(lineNumber)
                .message(message)
                .recommendation(recommendation)
                .createdAt(Instant.now())
                .build());
    }

    /** reasons: each row is {ruleId, blocking, message}. */
    private void verdict(AnalysisRun run, VerdictOutcome outcome, String[][] reasons) {
        Verdict verdict = verdictRepository.save(Verdict.builder()
                .analysisRun(run)
                .outcome(outcome)
                .createdAt(Instant.now())
                .build());

        for (String[] reason : reasons) {
            verdictReasonRepository.save(VerdictReasonEntity.builder()
                    .verdict(verdict)
                    .ruleId(reason[0])
                    .blocking(Boolean.parseBoolean(reason[1]))
                    .message(reason[2])
                    .createdAt(Instant.now())
                    .build());
        }
    }

    private void report(AnalysisRun run, AiReviewStatus aiReviewStatus) {
        engineeringReportRepository.save(EngineeringReport.builder()
                .analysisRun(run)
                .aiReviewStatus(aiReviewStatus)
                .publishedAt(Instant.now())
                .build());
    }

    /** findings: each row is {type, confidence, filePath, lineNumber, message, recommendation}. */
    private void aiReview(AnalysisRun run, String summary, Object[][] findings) {
        AIReviewRun aiRun = aiReviewRunRepository.save(AIReviewRun.builder()
                .analysisRun(run)
                .status(AIReviewRunStatus.COMPLETED)
                .provider("anthropic")
                .model("claude-opus-4-6")
                .promptVersion("v1")
                .summary(summary)
                .build());

        for (Object[] finding : findings) {
            aiReviewFindingRepository.save(AIReviewFindingEntity.builder()
                    .aiReviewRun(aiRun)
                    .type((AIReviewFindingType) finding[0])
                    .confidence((AIReviewConfidence) finding[1])
                    .filePath((String) finding[2])
                    .lineNumber((Integer) finding[3])
                    .message((String) finding[4])
                    .recommendation((String) finding[5])
                    .createdAt(Instant.now())
                    .build());
        }
    }
}
