package com.gatekeeper.policyfinding;

import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.policyfinding.dto.PolicyFindingFilter;
import com.gatekeeper.policyfinding.dto.PolicyFindingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side for PolicyFindingEntity (Milestone 5 Architecture, Section 7).
 * Introduced as a new, standalone service rather than added to the
 * write-side persistence service (originally PolicyFindingPersistenceService,
 * superseded by com.gatekeeper.orchestration.AnalysisResultPersistenceService
 * as of Sprint 3 Milestone 2 / ADR-025): that service exists purely to work
 * around the Spring AOP self-invocation pitfall from Milestone 4, and adding
 * an unrelated read concern to it would muddy that documented, narrow purpose
 * - see ADR-020 for why AnalysisRunService's reads were extended in place
 * instead, the opposite choice for a similar-looking question.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyFindingQueryService {

    private final PolicyFindingRepository policyFindingRepository;

    public PolicyFindingResponse findByIdOrThrow(Long id) {
        PolicyFindingEntity entity = policyFindingRepository.findWithContextById(id)
                .orElseThrow(() -> new ResourceNotFoundException("PolicyFinding not found with id: " + id));
        return PolicyFindingResponse.from(entity);
    }

    /**
     * Severity sort is handled specially (see PolicyFindingSpecifications.orderBySeverityRank):
     * when requested, any other sort keys on the incoming Pageable are dropped and
     * an unsorted Pageable is used instead, since Spring Data would otherwise overwrite
     * the Specification's custom ORDER BY the moment the Pageable's Sort is non-empty.
     */
    public Page<PolicyFindingResponse> findPage(PolicyFindingFilter filter, Pageable pageable) {
        Sort.Order severityOrder = pageable.getSort().getOrderFor("severity");
        Specification<PolicyFindingEntity> spec = PolicyFindingSpecifications.matching(filter);
        Pageable effectivePageable = pageable;

        if (severityOrder != null) {
            spec = spec.and(PolicyFindingSpecifications.orderBySeverityRank(severityOrder.getDirection()));
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }

        return policyFindingRepository.findAll(spec, effectivePageable).map(PolicyFindingResponse::from);
    }
}
