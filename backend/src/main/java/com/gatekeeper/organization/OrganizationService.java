package com.gatekeeper.organization;

import com.gatekeeper.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * GateKeeper is single-tenant for the MVP (docs/Database.md lists multi-tenant
 * organizations under Future Expansion). This service resolves the one
 * bootstrap-seeded Organization that every User and Repository belongs to.
 */
@Service
@RequiredArgsConstructor
public class OrganizationService {

    private static final String DEFAULT_ORGANIZATION_NAME = "Default Organization";

    private final OrganizationRepository organizationRepository;

    public Organization getDefaultOrganization() {
        return organizationRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Default organization '" + DEFAULT_ORGANIZATION_NAME + "' was not found. Was the Flyway migration applied?"));
    }
}
