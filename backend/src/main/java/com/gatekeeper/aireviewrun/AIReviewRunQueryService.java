package com.gatekeeper.aireviewrun;

import com.gatekeeper.aireviewengine.AIReviewConfidence;
import com.gatekeeper.aireviewfinding.AIReviewFindingRepository;
import com.gatekeeper.aireviewrun.dto.AIReviewRunDetailResponse;
import com.gatekeeper.aireviewrun.dto.AIReviewRunFilter;
import com.gatekeeper.aireviewrun.dto.AIReviewRunSummaryResponse;
import com.gatekeeper.exception.ResourceNotFoundException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side for AIReviewRun (Sprint 4 Milestone 4) - mirrors
 * AnalysisRunService's read methods (findSummaryPage/findDetailByIdOrThrow)
 * combined with SecurityFindingQueryService's standalone-service shape,
 * since AIReviewRun has no write-side service of its own to share with
 * (AIReviewResultPersistenceService in com.gatekeeper.orchestration owns
 * writes; this class is query-only).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AIReviewRunQueryService {

    private final AIReviewRunRepository aiReviewRunRepository;
    private final AIReviewFindingRepository aiReviewFindingRepository;

    /**
     * Enriches each row with its findings count via one supplementary batched
     * query over the page's ids (mirrors AnalysisRunService.findSummaryPage) -
     * deliberately not a single combined Specification + fetch-join + GROUP BY
     * + pagination query, which is fragile to get right under dynamic filters.
     */
    public Page<AIReviewRunSummaryResponse> findPage(AIReviewRunFilter filter, Pageable pageable) {
        Page<AIReviewRun> page = aiReviewRunRepository.findAll(AIReviewRunSpecifications.matching(filter), pageable);
        List<Long> ids = page.getContent().stream().map(AIReviewRun::getId).toList();
        Map<Long, Long> findingsTotals = countsByAiReviewRunId(ids);
        return page.map(run -> AIReviewRunSummaryResponse.from(run, findingsTotals.getOrDefault(run.getId(), 0L)));
    }

    public AIReviewRunDetailResponse findDetailByIdOrThrow(Long id) {
        AIReviewRun run = aiReviewRunRepository.findWithContextById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AIReviewRun not found with id: " + id));
        Map<AIReviewConfidence, Long> findingsByConfidence = new EnumMap<>(AIReviewConfidence.class);
        for (Object[] row : aiReviewFindingRepository.countByConfidenceForAiReviewRun(id)) {
            findingsByConfidence.put((AIReviewConfidence) row[0], (Long) row[1]);
        }
        return AIReviewRunDetailResponse.from(run, findingsByConfidence);
    }

    /** Skips the batch-count query entirely for an empty page, rather than issuing an "IN ()" query. */
    private Map<Long, Long> countsByAiReviewRunId(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> totals = new HashMap<>();
        for (Object[] row : aiReviewFindingRepository.countByAiReviewRunIdIn(ids)) {
            totals.put((Long) row[0], (Long) row[1]);
        }
        return totals;
    }
}
