package com.gatekeeper.policyconfiguration;

import com.gatekeeper.auditlog.AuditEvent;
import com.gatekeeper.auditlog.AuditEventType;
import com.gatekeeper.auditlog.AuditLogService;
import com.gatekeeper.auditlog.AuditTargetType;
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
    private final AuditLogService auditLogService;

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
    public PolicyConfigurationResponse upsert(
            Long organizationId, String ruleId, UpdatePolicyConfigurationRequest request, Long actorId) {
        PolicyRule rule = findRuleOrThrow(ruleId);
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found with id: " + organizationId));

        PolicyConfiguration existing = policyConfigurationRepository
                .findByOrganizationIdAndRuleId(organizationId, ruleId)
                .orElse(null);
        boolean previousEnabled = existing == null ? true : existing.isEnabled();
        PolicySeverity previousSeverityOverride = existing == null ? null : existing.getSeverityOverride();

        PolicyConfiguration configuration = existing == null
                ? PolicyConfiguration.builder().organization(organization).ruleId(ruleId).build()
                : existing;
        configuration.setEnabled(request.enabled());
        configuration.setSeverityOverride(request.severityOverride());
        policyConfigurationRepository.save(configuration);

        auditLogService.record(AuditEvent.builder()
                .eventType(AuditEventType.POLICY_CONFIGURATION_CHANGED)
                .organizationId(organizationId)
                .actorId(actorId)
                .targetType(AuditTargetType.POLICY_RULE)
                .targetId(ruleId)
                .oldValue(policyConfigurationValue(previousEnabled, previousSeverityOverride))
                .newValue(policyConfigurationValue(request.enabled(), request.severityOverride()))
                .summary("Policy rule '" + ruleId + "' configuration updated.")
                .build());

        return PolicyConfigurationResponse.from(rule, configuration.isEnabled(), configuration.getSeverityOverride());
    }

    /** Idempotent: deletes the override row if one exists, otherwise does nothing - either way the rule ends up at its own default. */
    @Transactional
    public PolicyConfigurationResponse resetToDefault(Long organizationId, String ruleId, Long actorId) {
        PolicyRule rule = findRuleOrThrow(ruleId);
        PolicyConfiguration existing = policyConfigurationRepository
                .findByOrganizationIdAndRuleId(organizationId, ruleId)
                .orElse(null);
        if (existing != null) {
            boolean previousEnabled = existing.isEnabled();
            PolicySeverity previousSeverityOverride = existing.getSeverityOverride();
            policyConfigurationRepository.delete(existing);

            auditLogService.record(AuditEvent.builder()
                    .eventType(AuditEventType.POLICY_CONFIGURATION_CHANGED)
                    .organizationId(organizationId)
                    .actorId(actorId)
                    .targetType(AuditTargetType.POLICY_RULE)
                    .targetId(ruleId)
                    .oldValue(policyConfigurationValue(previousEnabled, previousSeverityOverride))
                    .newValue(policyConfigurationValue(true, null))
                    .summary("Policy rule '" + ruleId + "' reset to default configuration.")
                    .build());
        }
        return PolicyConfigurationResponse.defaultFor(rule);
    }

    private Map<String, Object> policyConfigurationValue(boolean enabled, PolicySeverity severityOverride) {
        Map<String, Object> value = new HashMap<>();
        value.put("enabled", enabled);
        value.put("severityOverride", severityOverride == null ? null : severityOverride.name());
        return value;
    }

    private PolicyRule findRuleOrThrow(String ruleId) {
        return policyRules.stream()
                .filter(rule -> rule.id().equals(ruleId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No PolicyRule found with id: " + ruleId));
    }
}
