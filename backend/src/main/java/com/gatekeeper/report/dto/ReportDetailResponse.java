package com.gatekeeper.report.dto;

import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.analysisrun.dto.PullRequestReference;
import com.gatekeeper.analysisrun.dto.RepositoryReference;
import com.gatekeeper.aireviewfinding.dto.AIReviewFindingResponse;
import com.gatekeeper.auditlog.dto.AuditLogResponse;
import com.gatekeeper.policyfinding.dto.PolicyFindingResponse;
import com.gatekeeper.report.AiReviewStatus;
import com.gatekeeper.report.EngineeringReport;
import com.gatekeeper.securityfinding.dto.SecurityFindingResponse;
import com.gatekeeper.verdict.dto.VerdictReasonSummary;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.time.Instant;
import java.util.List;

/**
 * The single unified report object for GET /api/v1/analysis-runs/{id}/report
 * (Unified Engineering Report Architecture, Section 9) - "a single, unified
 * report combining Policy, Security, and AI findings" plus the governance
 * Verdict and the publication's own audit trail, exactly the composition
 * docs/Database.md and docs/Domain-Model.md describe for "Engineering
 * Report."
 * <p>
 * Deliberately reuses four already-canonical DTOs rather than duplicating
 * near-identical shapes: {@link PolicyFindingResponse}, {@link SecurityFindingResponse},
 * and {@link AIReviewFindingResponse} are each already the one canonical
 * "finding view" for their own list endpoints; {@link VerdictReasonSummary}
 * is the established one-deliberate-exception-to-duplication type (Sprint 5
 * Architecture, Section 14). Composing this response duplicates no new
 * shape - see ReportQueryService for how each list is assembled.
 * <p>
 * repository/pullRequest reuse AnalysisRunDetailResponse's own
 * RepositoryReference/PullRequestReference records rather than introducing
 * near-identical ones here - the richer, more recently established
 * convention (over VerdictDetailResponse's flatter, older
 * repositoryFullName/pullRequestNumber pair), chosen because this milestone's
 * own requirement list calls out "Repository information" and "Pull Request
 * information" as their own composed sections, not just enough context to
 * label a row.
 * <p>
 * aiFindings is empty whenever aiReviewStatus is not INCLUDED (UNAVAILABLE or
 * DISABLED) - the *why* AI content is missing stays on the AIReviewRun's own
 * failureReason field (visible via the existing AI Review Run API), not
 * duplicated here (ADR-050).
 */
public record ReportDetailResponse(
        Long id,
        Long analysisRunId,
        AnalysisRunStatus analysisRunStatus,
        AnalysisRunTriggerReason triggerReason,
        String commitSha,
        Instant analysisRunCreatedAt,
        Instant analysisRunUpdatedAt,
        RepositoryReference repository,
        PullRequestReference pullRequest,
        List<PolicyFindingResponse> policyFindings,
        List<SecurityFindingResponse> securityFindings,
        AiReviewStatus aiReviewStatus,
        List<AIReviewFindingResponse> aiFindings,
        VerdictOutcome verdictOutcome,
        List<VerdictReasonSummary> verdictReasons,
        List<AuditLogResponse> auditTrail,
        Instant publishedAt) {

    public static ReportDetailResponse from(
            EngineeringReport report,
            List<PolicyFindingResponse> policyFindings,
            List<SecurityFindingResponse> securityFindings,
            List<AIReviewFindingResponse> aiFindings,
            VerdictOutcome verdictOutcome,
            List<VerdictReasonSummary> verdictReasons,
            List<AuditLogResponse> auditTrail) {
        AnalysisRun analysisRun = report.getAnalysisRun();
        return new ReportDetailResponse(
                report.getId(),
                analysisRun.getId(),
                analysisRun.getStatus(),
                analysisRun.getTriggerReason(),
                analysisRun.getCommitSha(),
                analysisRun.getCreatedAt(),
                analysisRun.getUpdatedAt(),
                RepositoryReference.from(analysisRun.getPullRequest().getRepository()),
                PullRequestReference.from(analysisRun.getPullRequest()),
                policyFindings,
                securityFindings,
                report.getAiReviewStatus(),
                aiFindings,
                verdictOutcome,
                verdictReasons,
                auditTrail,
                report.getPublishedAt());
    }
}
