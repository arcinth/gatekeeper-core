package com.gatekeeper.verdict;

import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.verdict.dto.VerdictDetailResponse;
import com.gatekeeper.verdict.dto.VerdictFilter;
import com.gatekeeper.verdict.dto.VerdictReasonSummary;
import com.gatekeeper.verdict.dto.VerdictSummaryResponse;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side for Verdict (Sprint 5 Milestone 3) - mirrors
 * AIReviewRunQueryService's shape exactly (a standalone query service, no
 * write-side sharing: AnalysisResultPersistenceService in
 * com.gatekeeper.orchestration owns writes; this class is query-only).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerdictQueryService {

    private final VerdictRepository verdictRepository;
    private final VerdictReasonRepository verdictReasonRepository;

    /**
     * Enriches each row with its reasons count via one supplementary batched
     * query over the page's ids (mirrors AIReviewRunQueryService.findPage).
     */
    public Page<VerdictSummaryResponse> findPage(VerdictFilter filter, Pageable pageable) {
        Page<Verdict> page = verdictRepository.findAll(VerdictSpecifications.matching(filter), pageable);
        List<Long> ids = page.getContent().stream().map(Verdict::getId).toList();
        Map<Long, Long> reasonsTotals = countsByVerdictId(ids);
        return page.map(verdict -> VerdictSummaryResponse.from(verdict, reasonsTotals.getOrDefault(verdict.getId(), 0L)));
    }

    public VerdictDetailResponse findDetailByIdOrThrow(Long id) {
        Verdict verdict = verdictRepository.findWithContextById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Verdict not found with id: " + id));
        List<VerdictReasonSummary> reasons = verdictReasonRepository.findByVerdictIdOrderById(id).stream()
                .map(VerdictReasonSummary::from)
                .toList();
        return VerdictDetailResponse.from(verdict, reasons);
    }

    /** Skips the batch-count query entirely for an empty page, rather than issuing an "IN ()" query. */
    private Map<Long, Long> countsByVerdictId(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> totals = new HashMap<>();
        for (Object[] row : verdictReasonRepository.countByVerdictIdIn(ids)) {
            totals.put((Long) row[0], (Long) row[1]);
        }
        return totals;
    }
}
