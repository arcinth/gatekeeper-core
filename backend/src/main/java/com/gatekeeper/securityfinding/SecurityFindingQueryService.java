package com.gatekeeper.securityfinding;

import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.securityfinding.dto.SecurityFindingFilter;
import com.gatekeeper.securityfinding.dto.SecurityFindingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side for SecurityFindingEntity (Security Engine Architecture, Section
 * 13) - mirrors com.gatekeeper.policyfinding.PolicyFindingQueryService
 * exactly, including the same rationale for being a new, standalone service
 * (ADR-020's reasoning applies identically here).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecurityFindingQueryService {

    private final SecurityFindingRepository securityFindingRepository;

    public SecurityFindingResponse findByIdOrThrow(Long id) {
        SecurityFindingEntity entity = securityFindingRepository.findWithContextById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SecurityFinding not found with id: " + id));
        return SecurityFindingResponse.from(entity);
    }

    /**
     * Severity sort is handled specially (see SecurityFindingSpecifications.orderBySeverityRank):
     * when requested, any other sort keys on the incoming Pageable are dropped and
     * an unsorted Pageable is used instead, since Spring Data would otherwise overwrite
     * the Specification's custom ORDER BY the moment the Pageable's Sort is non-empty.
     */
    public Page<SecurityFindingResponse> findPage(SecurityFindingFilter filter, Pageable pageable) {
        Sort.Order severityOrder = pageable.getSort().getOrderFor("severity");
        Specification<SecurityFindingEntity> spec = SecurityFindingSpecifications.matching(filter);
        Pageable effectivePageable = pageable;

        if (severityOrder != null) {
            spec = spec.and(SecurityFindingSpecifications.orderBySeverityRank(severityOrder.getDirection()));
            effectivePageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        }

        return securityFindingRepository.findAll(spec, effectivePageable).map(SecurityFindingResponse::from);
    }
}
