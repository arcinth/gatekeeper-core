package com.gatekeeper.repository;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewfinding.AIReviewFindingRepository;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.report.AiReviewStatus;
import com.gatekeeper.report.EngineeringReportRepository;
import com.gatekeeper.repository.dto.RepositoryGovernanceResponse;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import com.gatekeeper.verdict.VerdictRepository;
import com.gatekeeper.verdictengine.VerdictOutcome;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs GET /api/v1/repositories/{id}/governance (Repository Governance View
 * Architecture, Section 4/6). Structurally identical to
 * DashboardAggregationService - the same eight-GROUP-BY-query-per-request
 * shape, the same windowDays default/override handling, the same
 * toEnumCountMap/sum helpers - just filtered to one repositoryId instead of
 * the whole organization (ADR-052: kept as a sibling, not merged into
 * DashboardAggregationService, since the response shape differs).
 * <p>
 * Pure read-side aggregation: no write path, no cache, no scheduled job, no
 * event, and no change to any existing engine, DashboardAggregationService,
 * or report generation/persistence logic (ADR-051).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RepositoryGovernanceService {

    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final RepositoryService repositoryService;
    private final AnalysisRunRepository analysisRunRepository;
    private final PolicyFindingRepository policyFindingRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final AIReviewFindingRepository aiReviewFindingRepository;
    private final VerdictRepository verdictRepository;
    private final EngineeringReportRepository engineeringReportRepository;

    /**
     * Repository existence is validated by delegating to
     * RepositoryService.findById, the same lookup RepositoryController
     * already uses - a 404 for an unknown id is exactly its existing
     * ResourceNotFoundException, not a new error path.
     */
    public RepositoryGovernanceResponse getGovernanceSummary(Long repositoryId, Integer windowDays) {
        Repository repository = repositoryService.findById(repositoryId);

        int effectiveWindowDays = windowDays != null ? windowDays : DEFAULT_WINDOW_DAYS;
        Instant since = effectiveWindowDays > 0
                ? Instant.now().minus(effectiveWindowDays, ChronoUnit.DAYS)
                : Instant.EPOCH;

        Map<AnalysisRunStatus, Long> runsByStatus = toEnumCountMap(
                analysisRunRepository.countByStatusSinceForRepository(since, repositoryId), AnalysisRunStatus.class);
        Map<PolicySeverity, Long> findingsBySeverity = toEnumCountMap(
                policyFindingRepository.countBySeveritySinceForRepository(since, repositoryId), PolicySeverity.class);
        Map<PolicyCategory, Long> findingsByCategory = toEnumCountMap(
                policyFindingRepository.countByCategorySinceForRepository(since, repositoryId), PolicyCategory.class);
        Map<SecuritySeverity, Long> securityFindingsBySeverity = toEnumCountMap(
                securityFindingRepository.countBySeveritySinceForRepository(since, repositoryId), SecuritySeverity.class);
        Map<SecurityCategory, Long> securityFindingsByCategory = toEnumCountMap(
                securityFindingRepository.countByCategorySinceForRepository(since, repositoryId), SecurityCategory.class);
        Map<AIReviewConfidence, Long> aiReviewFindingsByConfidence = toEnumCountMap(
                aiReviewFindingRepository.countByConfidenceSinceForRepository(since, repositoryId), AIReviewConfidence.class);
        Map<AIReviewFindingType, Long> aiReviewFindingsByType = toEnumCountMap(
                aiReviewFindingRepository.countByTypeSinceForRepository(since, repositoryId), AIReviewFindingType.class);
        Map<VerdictOutcome, Long> verdictsByOutcome = toEnumCountMap(
                verdictRepository.countByOutcomeSinceForRepository(since, repositoryId), VerdictOutcome.class);
        Map<AiReviewStatus, Long> reportsByAiStatus = toEnumCountMap(
                engineeringReportRepository.countByAiReviewStatusSinceForRepository(since, repositoryId), AiReviewStatus.class);

        return new RepositoryGovernanceResponse(
                repository.getId(),
                repository.getFullName(),
                effectiveWindowDays,
                sum(runsByStatus),
                runsByStatus,
                sum(findingsBySeverity),
                findingsBySeverity,
                findingsByCategory,
                sum(securityFindingsBySeverity),
                securityFindingsBySeverity,
                securityFindingsByCategory,
                sum(aiReviewFindingsByConfidence),
                aiReviewFindingsByConfidence,
                aiReviewFindingsByType,
                sum(verdictsByOutcome),
                verdictsByOutcome,
                sum(reportsByAiStatus),
                reportsByAiStatus);
    }

    @SuppressWarnings("unchecked")
    private <E extends Enum<E>> Map<E, Long> toEnumCountMap(List<Object[]> rows, Class<E> enumType) {
        Map<E, Long> result = new EnumMap<>(enumType);
        for (Object[] row : rows) {
            result.put((E) row[0], (Long) row[1]);
        }
        return result;
    }

    private long sum(Map<?, Long> counts) {
        return counts.values().stream().mapToLong(Long::longValue).sum();
    }
}
