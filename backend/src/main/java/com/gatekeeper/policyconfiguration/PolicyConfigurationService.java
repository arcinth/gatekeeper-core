package com.gatekeeper.policyconfiguration;

import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.organization.OrganizationRepository;
import com.gatekeeper.policy.PolicyConfigurationSet;
import com.gatekeeper.policy.PolicyRule;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyconfiguration.dto.PolicyConfigurationResponse;
import com.gatekeeper.policyconfiguration.dto.UpdatePolicyConfigurationRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The read/write boundary for organization policy overrides (Milestone 6:
 * Policy Management). The rule catalog itself always comes from the live
 * {@code List<PolicyRule>} Spring discovers - the same beans PolicyEngine
 * holds - never from {@link PolicyConfigurationRepository}, which stores
 * only the override rows. See docs/Policy-Development.md.
 * <p>
 * {@link #buildConfigurationSet} is what {@code PolicyEngineService} calls
 * before invoking {@code PolicyEngine.evaluate} - it is the one place a
 * database query turns into the immutable snapshot PolicyEngine consumes as
 * a plain parameter.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyConfigurationService {

    private final List<PolicyRule> policyRules;
    private final PolicyConfigurationRepository policyConfigurationRepository;
    private final OrganizationRepository organizationRepository;

    /** The immutable snapshot PolicyEngine.evaluate consumes - built fresh for every evaluation, never cached or shared. */
    public PolicyConfigurationSet buildConfigurationSet(Long organizationId) {
        List<PolicyConfiguration> overrides = policyConfigurationRepository.findByOrganizationId(organizationId);
        Map<String, Boolean> enabledByRuleId = new HashMap<>();
        Map<String, PolicySeverity> severityOverrideByRuleId = new HashMap<>();
        for (PolicyConfiguration override : overrides) {
            enabledByRuleId.put(override.getRuleId(), override.isEnabled());
            if (override.getSeverityOverride() != null) {
                severityOverrideByRuleId.put(override.getRuleId(), override.getSeverityOverride());
            }
        }
        return new PolicyConfigurationSet(enabledByRuleId, severityOverrideByRuleId);
    }

    /** The full rule catalog, merged with this organization's effective configuration for each rule. */
    public List<PolicyConfigurationResponse> findCatalogForOrganization(Long organizationId) {
        Map<String, PolicyConfiguration> overridesByRuleId = policyConfigurationRepository
                .findByOrganizationId(organizationId).stream()
                .collect(Collectors.toMap(PolicyConfiguration::getRuleId, override -> override));

        return policyRules.stream()
                .map(rule -> {
                    PolicyConfiguration override = overridesByRuleId.get(rule.id());
                    return override == null
                            ? PolicyConfigurationResponse.defaultFor(rule)
                            : PolicyConfigurationResponse.from(rule, override.isEnabled(), override.getSeverityOverride());
                })
                .toList();
    }

    @Transactional
    public PolicyConfigurationResponse upsert(Long organizationId, String ruleId, UpdatePolicyConfigurationRequest request) {
        PolicyRule rule = findRuleOrThrow(ruleId);
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + organizationId));

        PolicyConfiguration configuration = policyConfigurationRepository
                .findByOrganizationIdAndRuleId(organizationId, ruleId)
                .orElseGet(() -> PolicyConfiguration.builder().organization(organization).ruleId(ruleId).build());
        configuration.setEnabled(request.enabled());
        configuration.setSeverityOverride(request.severityOverride());
        policyConfigurationRepository.save(configuration);

        return PolicyConfigurationResponse.from(rule, configuration.isEnabled(), configuration.getSeverityOverride());
    }

    /** Idempotent: deletes the override row if one exists, otherwise does nothing - either way the rule ends up at its own default. */
    @Transactional
    public PolicyConfigurationResponse resetToDefault(Long organizationId, String ruleId) {
        PolicyRule rule = findRuleOrThrow(ruleId);
        policyConfigurationRepository.findByOrganizationIdAndRuleId(organizationId, ruleId)
                .ifPresent(policyConfigurationRepository::delete);
        return PolicyConfigurationResponse.defaultFor(rule);
    }

    private PolicyRule findRuleOrThrow(String ruleId) {
        return policyRules.stream()
                .filter(rule -> rule.id().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No PolicyRule found with id: " + ruleId));
    }
}
