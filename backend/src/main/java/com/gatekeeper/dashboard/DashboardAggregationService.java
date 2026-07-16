package com.gatekeeper.dashboard;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewengine.AIReviewFindingType;
import com.gatekeeper.aireviewfinding.AIReviewFindingRepository;
import com.gatekeeper.aireviewrun.AIReviewRunRepository;
import com.gatekeeper.aireviewrun.AIReviewRunStatus;
import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.repository.RepositoryRepository;
import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.securityfinding.SecurityFindingRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs GET /api/v1/dashboard/overview. Extended for Security Findings
 * (Security Engine Architecture, Section 14) alongside the existing Policy
 * aggregation, and again for AI Review (Sprint 4 Milestone 4) - every new
 * count is computed by the same kind of dedicated GROUP BY repository query
 * as Policy's, never fetched-and-counted in the JVM.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardAggregationService {

    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final AnalysisRunRepository analysisRunRepository;
    private final PolicyFindingRepository policyFindingRepository;
    private final SecurityFindingRepository securityFindingRepository;
    private final AIReviewRunRepository aiReviewRunRepository;
    private final AIReviewFindingRepository aiReviewFindingRepository;
    private final RepositoryRepository repositoryRepository;

    public DashboardOverviewResponse getOverview(Integer windowDays) {
        int effectiveWindowDays = windowDays != null ? windowDays : DEFAULT_WINDOW_DAYS;
        Instant since = effectiveWindowDays > 0
                ? Instant.now().minus(effectiveWindowDays, ChronoUnit.DAYS)
                : Instant.EPOCH;

        Map<AnalysisRunStatus, Long> runsByStatus =
                toEnumCountMap(analysisRunRepository.countByStatusSince(since), AnalysisRunStatus.class);
        Map<PolicySeverity, Long> findingsBySeverity =
                toEnumCountMap(policyFindingRepository.countBySeveritySince(since), PolicySeverity.class);
        Map<PolicyCategory, Long> findingsByCategory =
                toEnumCountMap(policyFindingRepository.countByCategorySince(since), PolicyCategory.class);
        Map<SecuritySeverity, Long> securityFindingsBySeverity =
                toEnumCountMap(securityFindingRepository.countBySeveritySince(since), SecuritySeverity.class);
        Map<SecurityCategory, Long> securityFindingsByCategory =
                toEnumCountMap(securityFindingRepository.countByCategorySince(since), SecurityCategory.class);
        Map<AIReviewRunStatus, Long> aiReviewRunsByStatus =
                toEnumCountMap(aiReviewRunRepository.countByStatusSince(since), AIReviewRunStatus.class);
        Map<AIReviewConfidence, Long> aiReviewFindingsByConfidence =
                toEnumCountMap(aiReviewFindingRepository.countByConfidenceSince(since), AIReviewConfidence.class);
        Map<AIReviewFindingType, Long> aiReviewFindingsByType =
                toEnumCountMap(aiReviewFindingRepository.countByTypeSince(since), AIReviewFindingType.class);

        return new DashboardOverviewResponse(
                effectiveWindowDays,
                repositoryRepository.count(),
                sum(runsByStatus),
                runsByStatus,
                sum(findingsBySeverity),
                findingsBySeverity,
                findingsByCategory,
                sum(securityFindingsBySeverity),
                securityFindingsBySeverity,
                securityFindingsByCategory,
                sum(aiReviewRunsByStatus),
                aiReviewRunsByStatus,
                sum(aiReviewFindingsByConfidence),
                aiReviewFindingsByConfidence,
                aiReviewFindingsByType);
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
