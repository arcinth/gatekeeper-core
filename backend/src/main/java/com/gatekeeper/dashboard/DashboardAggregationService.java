package com.gatekeeper.dashboard;

import com.gatekeeper.analysisrun.AnalysisRunRepository;
import com.gatekeeper.analysisrun.AnalysisRunStatus;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyfinding.PolicyFindingRepository;
import com.gatekeeper.repository.RepositoryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs GET /api/v1/dashboard/overview - the endpoint DashboardController's
 * original Javadoc deferred until Analysis Runs and Findings existed to
 * aggregate (Milestone 5 Architecture, Section 1). Every count is computed by
 * a dedicated GROUP BY repository query, never by fetching rows and counting
 * them in the JVM (Section 10).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardAggregationService {

    private static final int DEFAULT_WINDOW_DAYS = 30;

    private final AnalysisRunRepository analysisRunRepository;
    private final PolicyFindingRepository policyFindingRepository;
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

        return new DashboardOverviewResponse(
                effectiveWindowDays,
                repositoryRepository.count(),
                sum(runsByStatus),
                runsByStatus,
                sum(findingsBySeverity),
                findingsBySeverity,
                findingsByCategory);
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
