package com.gatekeeper.policyconfiguration;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyConfigurationRepository extends JpaRepository<PolicyConfiguration, Long> {

    /** Every override an organization has ever set - the raw material PolicyConfigurationSet is built from. */
    List<PolicyConfiguration> findByOrganizationId(Long organizationId);

    Optional<PolicyConfiguration> findByOrganizationIdAndRuleId(Long organizationId, String ruleId);
}
