package com.gatekeeper.aireviewfinding;

import com.gatekeeper.aireviewfinding.dto.AIReviewFindingFilter;
import com.gatekeeper.aireviewfinding.dto.AIReviewFindingResponse;
import com.gatekeeper.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side for AIReviewFindingEntity (Sprint 4 Milestone 4) - mirrors
 * SecurityFindingQueryService exactly, confidence-rank sorting standing in
 * for severity-rank sorting.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AIReviewFindingQueryService {

    private final AIReviewFindingRepository aiReviewFindingRepository;

    public AIReviewFindingResponse findByIdOrThrow(Long id) {
        AIReviewFindingEntity entity = aiReviewFindingRepository.findWithContextById(id)
                .orElseThrow(() -> new ResourceNotFoundException("AIReviewFinding not found with id: " + id));
        return AIReviewFindingResponse.from(entity);
    }

    /**
     * Confidence sort is handled specially (see
     * AIReviewFindingSpecifications.orderByConfidenceRank): when requested,
     * any other sort keys on the incoming Pageable are dropped and an
     * unsorted Pageable is used instead, since Spring Data would otherwise
     * overwrite the Specification's custom ORDER BY the moment the
     * Pageable's Sort is non-empty.
     */
    public Page<AIReviewFindingResponse> findPage(AIReviewFindingFilter filter, Pageable pageable) {
        Sort.Order confidenceOrder = pageable.getSort().getOrderFor("confidence");
        Specification<AIReviewFindingEntity> spec = AIReviewFindingSpecifications.matching(filter);
        Pageable effectivePageable = pageable;

        if (confidenceOrder != null) {
            spec = spec.and(AIReviewFindingSpecifications.orderByConfidenceRank(confidenceOrder.getDirection()));
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }

        return aiReviewFindingRepository.findAll(spec, effectivePageable).map(AIReviewFindingResponse::from);
    }
}
