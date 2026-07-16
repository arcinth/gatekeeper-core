package com.gatekeeper.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewfinding.AIReviewFindingEntity;
import com.gatekeeper.aireviewfinding.AIReviewFindingRepository;
import com.gatekeeper.aireviewrun.AIReviewRun;
import com.gatekeeper.aireviewrun.AIReviewRunRepository;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import com.gatekeeper.analysisrun.AnalysisRun;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunTriggerReason;
import com.gatekeeper.auditlog.AuditEventType;
import com.gatekeeper.auditlog.AuditLog;
import com.gatekeeper.auditlog.AuditLogRepository;
import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.PolicyFindingEntity;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.pullrequest.PullRequest;
import com.gatekeeper.report.dto.ReportDetailResponse;
import com.gatekeeper.repository.Repository;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.SecurityFindingEntity;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import com.gatekeeper.verdict.Verdict;
import com.gatekeeper.verdict.VerdictReasonEntity;
import com.gatekeeper.verdict.VerdictReasonRepository;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ReportQueryServiceTest {

    private static final Long ANALYSIS_RUN_ID = 1L;

    private final EngineeringReportRepository engineeringReportRepository = mock(EngineeringReportRepository.class);
    private final PolicyFindingRepository policyFindingRepository = mock(PolicyFindingRepository.class);
    private final SecurityFindingRepository securityFindingRepository = mock(SecurityFindingRepository.class);
    private final AIReviewRunRepository aiReviewRunRepository = mock(AIReviewRunRepository.class);
    private final AIReviewFindingRepository aiReviewFindingRepository = mock(AIReviewFindingRepository.class);
    private final VerdictRepository verdictRepository = mock(VerdictRepository.class);
    private final VerdictReasonRepository verdictReasonRepository = mock(VerdictReasonRepository.class);
    private final AuditLogRepository auditLogRepository = mock(AuditLogRepository.class);

    private final ReportQueryService service = new ReportQueryService(
            engineeringReportRepository, policyFindingRepository, securityFindingRepository,
            aiReviewRunRepository, aiReviewFindingRepository, verdictRepository, verdictReasonRepository,
            auditLogRepository);

    @Test
    void findByAnalysisRunId_delegatesToTheRepository() {
        EngineeringReport report = EngineeringReport.builder().aiReviewStatus(AiReviewStatus.DISABLED).build();
        when(engineeringReportRepository.findByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.of(report));

        assertThat(service.findByAnalysisRunId(ANALYSIS_RUN_ID)).contains(report);
    }

    @Test
    void findByAnalysisRunIdOrThrow_throwsResourceNotFoundWhenNoReportHasBeenPublished() {
        when(engineeringReportRepository.findWithContextByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByAnalysisRunIdOrThrow(ANALYSIS_RUN_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findByAnalysisRunIdOrThrow_throwsIllegalStateWhenAVerdictIsMissingDespiteAReportExisting() {
        AnalysisRun analysisRun = analysisRunWithFullContext();
        EngineeringReport report = engineeringReportFor(analysisRun, AiReviewStatus.DISABLED);
        when(engineeringReportRepository.findWithContextByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.of(report));
        when(policyFindingRepository.findByAnalysisRunIdOrderById(ANALYSIS_RUN_ID)).thenReturn(List.of());
        when(securityFindingRepository.findByAnalysisRunIdOrderById(ANALYSIS_RUN_ID)).thenReturn(List.of());
        when(verdictRepository.findByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByAnalysisRunIdOrThrow(ANALYSIS_RUN_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("violates the publication invariant");
    }

    @Test
    void findByAnalysisRunIdOrThrow_composesEveryFrozenSectionOfTheUnifiedReport() {
        AnalysisRun analysisRun = analysisRunWithFullContext();
        EngineeringReport report = engineeringReportFor(analysisRun, AiReviewStatus.DISABLED);
        when(engineeringReportRepository.findWithContextByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.of(report));

        PolicyFindingEntity policyFinding = PolicyFindingEntity.builder()
                .analysisRun(analysisRun).ruleId("TODO_COMMENT").category(PolicyCategory.MAINTAINABILITY)
                .severity(PolicySeverity.LOW).filePath("a.txt").lineNumber(1).message("m").recommendation("r").build();
        when(policyFindingRepository.findByAnalysisRunIdOrderById(ANALYSIS_RUN_ID)).thenReturn(List.of(policyFinding));

        SecurityFindingEntity securityFinding = SecurityFindingEntity.builder()
                .analysisRun(analysisRun).ruleId("HARDCODED_SECRET").category(SecurityCategory.SECRETS_EXPOSURE)
                .severity(SecuritySeverity.CRITICAL).filePath("b.txt").lineNumber(2).message("m2").recommendation("r2").build();
        when(securityFindingRepository.findByAnalysisRunIdOrderById(ANALYSIS_RUN_ID)).thenReturn(List.of(securityFinding));

        Verdict verdict = Verdict.builder().analysisRun(analysisRun).outcome(VerdictOutcome.BLOCKED).build();
        ReflectionTestUtils.setField(verdict, "id", 5L);
        when(verdictRepository.findByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.of(verdict));
        VerdictReasonEntity reason = VerdictReasonEntity.builder()
                .verdict(verdict).ruleId("CRITICAL_SECURITY_FINDING").blocking(true).message("blocked!").build();
        when(verdictReasonRepository.findByVerdictIdOrderById(5L)).thenReturn(List.of(reason));

        AuditLog auditEntry = AuditLog.builder()
                .organization(analysisRun.getPullRequest().getRepository().getOrganization())
                .analysisRun(analysisRun).eventType(AuditEventType.ENGINEERING_REPORT_PUBLISHED)
                .summary("Engineering report published for analysis run 1.").build();
        when(auditLogRepository.findByAnalysisRunIdOrderByOccurredAt(ANALYSIS_RUN_ID)).thenReturn(List.of(auditEntry));

        ReportDetailResponse response = service.findByAnalysisRunIdOrThrow(ANALYSIS_RUN_ID);

        assertThat(response.analysisRunId()).isEqualTo(ANALYSIS_RUN_ID);
        assertThat(response.analysisRunStatus()).isEqualTo(AnalysisRunStatus.COMPLETED);
        assertThat(response.triggerReason()).isEqualTo(AnalysisRunTriggerReason.OPENED);
        assertThat(response.commitSha()).isEqualTo("cafebabe");
        assertThat(response.repository().fullName()).isEqualTo("org/core");
        assertThat(response.pullRequest().number()).isEqualTo(7);
        assertThat(response.policyFindings()).hasSize(1);
        assertThat(response.policyFindings().get(0).ruleId()).isEqualTo("TODO_COMMENT");
        assertThat(response.securityFindings()).hasSize(1);
        assertThat(response.securityFindings().get(0).ruleId()).isEqualTo("HARDCODED_SECRET");
        assertThat(response.aiReviewStatus()).isEqualTo(AiReviewStatus.DISABLED);
        assertThat(response.aiFindings()).isEmpty();
        assertThat(response.verdictOutcome()).isEqualTo(VerdictOutcome.BLOCKED);
        assertThat(response.verdictReasons()).hasSize(1);
        assertThat(response.verdictReasons().get(0).ruleId()).isEqualTo("CRITICAL_SECURITY_FINDING");
        assertThat(response.auditTrail()).hasSize(1);
        assertThat(response.auditTrail().get(0).eventType()).isEqualTo(AuditEventType.ENGINEERING_REPORT_PUBLISHED);
        assertThat(response.publishedAt()).isEqualTo(report.getPublishedAt());
    }

    @Test
    void findByAnalysisRunIdOrThrow_includesAiFindingsWhenAiReviewStatusIsIncluded() {
        AnalysisRun analysisRun = analysisRunWithFullContext();
        EngineeringReport report = engineeringReportFor(analysisRun, AiReviewStatus.INCLUDED);
        when(engineeringReportRepository.findWithContextByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.of(report));
        when(policyFindingRepository.findByAnalysisRunIdOrderById(ANALYSIS_RUN_ID)).thenReturn(List.of());
        when(securityFindingRepository.findByAnalysisRunIdOrderById(ANALYSIS_RUN_ID)).thenReturn(List.of());
        stubApprovedVerdictWithNoReasons(analysisRun);
        when(auditLogRepository.findByAnalysisRunIdOrderByOccurredAt(ANALYSIS_RUN_ID)).thenReturn(List.of());

        AIReviewRun aiReviewRun = AIReviewRun.builder()
                .analysisRun(analysisRun).status(AIReviewRunStatus.COMPLETED)
                .provider("anthropic-claude").model("claude-opus-4-6").promptVersion("v1").build();
        ReflectionTestUtils.setField(aiReviewRun, "id", 8L);
        when(aiReviewRunRepository.findByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.of(aiReviewRun));
        AIReviewFindingEntity aiFinding = AIReviewFindingEntity.builder()
                .aiReviewRun(aiReviewRun).type(AIReviewFindingType.POTENTIAL_BUG).confidence(AIReviewConfidence.HIGH)
                .filePath("c.txt").lineNumber(3).message("m3").build();
        when(aiReviewFindingRepository.findByAiReviewRunIdOrderById(8L)).thenReturn(List.of(aiFinding));

        ReportDetailResponse response = service.findByAnalysisRunIdOrThrow(ANALYSIS_RUN_ID);

        assertThat(response.aiReviewStatus()).isEqualTo(AiReviewStatus.INCLUDED);
        assertThat(response.aiFindings()).hasSize(1);
        assertThat(response.aiFindings().get(0).type()).isEqualTo(AIReviewFindingType.POTENTIAL_BUG);
    }

    @Test
    void findByAnalysisRunIdOrThrow_omitsAiFindingsAndNeverQueriesAiReviewRunWhenStatusIsUnavailable() {
        AnalysisRun analysisRun = analysisRunWithFullContext();
        EngineeringReport report = engineeringReportFor(analysisRun, AiReviewStatus.UNAVAILABLE);
        when(engineeringReportRepository.findWithContextByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.of(report));
        when(policyFindingRepository.findByAnalysisRunIdOrderById(ANALYSIS_RUN_ID)).thenReturn(List.of());
        when(securityFindingRepository.findByAnalysisRunIdOrderById(ANALYSIS_RUN_ID)).thenReturn(List.of());
        stubApprovedVerdictWithNoReasons(analysisRun);
        when(auditLogRepository.findByAnalysisRunIdOrderByOccurredAt(ANALYSIS_RUN_ID)).thenReturn(List.of());

        ReportDetailResponse response = service.findByAnalysisRunIdOrThrow(ANALYSIS_RUN_ID);

        assertThat(response.aiFindings()).isEmpty();
        verify(aiReviewRunRepository, never()).findByAnalysisRunId(any());
        verify(aiReviewFindingRepository, never()).findByAiReviewRunIdOrderById(any());
    }

    @Test
    void findByAnalysisRunIdOrThrow_omitsAiFindingsWhenStatusIsDisabled() {
        AnalysisRun analysisRun = analysisRunWithFullContext();
        EngineeringReport report = engineeringReportFor(analysisRun, AiReviewStatus.DISABLED);
        when(engineeringReportRepository.findWithContextByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.of(report));
        when(policyFindingRepository.findByAnalysisRunIdOrderById(ANALYSIS_RUN_ID)).thenReturn(List.of());
        when(securityFindingRepository.findByAnalysisRunIdOrderById(ANALYSIS_RUN_ID)).thenReturn(List.of());
        stubApprovedVerdictWithNoReasons(analysisRun);
        when(auditLogRepository.findByAnalysisRunIdOrderByOccurredAt(ANALYSIS_RUN_ID)).thenReturn(List.of());

        ReportDetailResponse response = service.findByAnalysisRunIdOrThrow(ANALYSIS_RUN_ID);

        assertThat(response.aiFindings()).isEmpty();
        verify(aiReviewRunRepository, never()).findByAnalysisRunId(any());
    }

    private void stubApprovedVerdictWithNoReasons(AnalysisRun analysisRun) {
        Verdict verdict = Verdict.builder().analysisRun(analysisRun).outcome(VerdictOutcome.APPROVED).build();
        ReflectionTestUtils.setField(verdict, "id", 5L);
        when(verdictRepository.findByAnalysisRunId(ANALYSIS_RUN_ID)).thenReturn(Optional.of(verdict));
        when(verdictReasonRepository.findByVerdictIdOrderById(5L)).thenReturn(List.of());
    }

    private EngineeringReport engineeringReportFor(AnalysisRun analysisRun, AiReviewStatus aiReviewStatus) {
        EngineeringReport report = EngineeringReport.builder()
                .analysisRun(analysisRun).aiReviewStatus(aiReviewStatus).build();
        ReflectionTestUtils.setField(report, "id", 3L);
        ReflectionTestUtils.setField(report, "publishedAt", Instant.now());
        return report;
    }

    private AnalysisRun analysisRunWithFullContext() {
        Organization organization = Organization.builder().name("acme").build();
        Repository repository = Repository.builder().fullName("org/core").organization(organization).build();
        PullRequest pullRequest = PullRequest.builder()
                .repository(repository).number(7).title("Add feature").authorLogin("octocat")
                .sourceBranch("feature").targetBranch("main").headSha("cafebabe").build();
        AnalysisRun analysisRun = AnalysisRun.builder()
                .pullRequest(pullRequest).commitSha("cafebabe").status(AnalysisRunStatus.COMPLETED)
                .triggerReason(AnalysisRunTriggerReason.OPENED).build();
        ReflectionTestUtils.setField(analysisRun, "id", ANALYSIS_RUN_ID);
        ReflectionTestUtils.setField(analysisRun, "createdAt", Instant.now());
        ReflectionTestUtils.setField(analysisRun, "updatedAt", Instant.now());
        return analysisRun;
    }
}
