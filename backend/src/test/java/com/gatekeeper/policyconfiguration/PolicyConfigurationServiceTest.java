package com.gatekeeper.policyconfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.exception.ResourceNotFoundException;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.organization.OrganizationRepository;
import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicyConfigurationSet;
import com.gatekeeper.policy.PolicyContext;
import com.gatekeeper.policy.PolicyFinding;
import com.gatekeeper.policy.PolicyRule;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.policyconfiguration.dto.PolicyConfigurationResponse;
import com.gatekeeper.policyconfiguration.dto.UpdatePolicyConfigurationRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PolicyConfigurationServiceTest {

    private static final Long ORG_ID = 7L;

    private final PolicyRule ruleA = testRule("RULE_A", PolicyCategory.CODE_QUALITY, PolicySeverity.LOW);
    private final PolicyRule ruleB = testRule("RULE_B", PolicyCategory.MAINTAINABILITY, PolicySeverity.MEDIUM);
    private final PolicyConfigurationRepository policyConfigurationRepository = mock(PolicyConfigurationRepository.class);
    private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
    private final PolicyConfigurationService service =
            new PolicyConfigurationService(List.of(ruleA, ruleB), policyConfigurationRepository, organizationRepository);

    @Test
    void buildConfigurationSet_withNoOverrides_behavesLikeEveryRuleAtItsDefault() {
        when(policyConfigurationRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of());

        PolicyConfigurationSet configuration = service.buildConfigurationSet(ORG_ID);

        assertThat(configuration.isEnabled("RULE_A")).isTrue();
        assertThat(configuration.severityOverride("RULE_A")).isEmpty();
    }

    @Test
    void buildConfigurationSet_reflectsPersistedOverrides() {
        PolicyConfiguration disabled = PolicyConfiguration.builder().ruleId("RULE_A").enabled(false).build();
        PolicyConfiguration overridden = PolicyConfiguration.builder()
                .ruleId("RULE_B").enabled(true).severityOverride(PolicySeverity.CRITICAL).build();
        when(policyConfigurationRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(disabled, overridden));

        PolicyConfigurationSet configuration = service.buildConfigurationSet(ORG_ID);

        assertThat(configuration.isEnabled("RULE_A")).isFalse();
        assertThat(configuration.severityOverride("RULE_B")).contains(PolicySeverity.CRITICAL);
    }

    @Test
    void findCatalogForOrganization_returnsEveryDiscoveredRuleEvenWithNoOverridesPersisted() {
        when(policyConfigurationRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of());

        List<PolicyConfigurationResponse> catalog = service.findCatalogForOrganization(ORG_ID);

        assertThat(catalog).hasSize(2);
        assertThat(catalog).extracting(PolicyConfigurationResponse::ruleId).containsExactlyInAnyOrder("RULE_A", "RULE_B");
        assertThat(catalog).allMatch(entry -> !entry.overridden());
    }

    @Test
    void findCatalogForOrganization_mergesAPersistedOverrideIntoTheCatalogEntry() {
        PolicyConfiguration disabled = PolicyConfiguration.builder().ruleId("RULE_A").enabled(false).build();
        when(policyConfigurationRepository.findByOrganizationId(ORG_ID)).thenReturn(List.of(disabled));

        List<PolicyConfigurationResponse> catalog = service.findCatalogForOrganization(ORG_ID);

        PolicyConfigurationResponse ruleAEntry = catalog.stream().filter(e -> e.ruleId().equals("RULE_A")).findFirst().orElseThrow();
        assertThat(ruleAEntry.enabled()).isFalse();
        assertThat(ruleAEntry.overridden()).isTrue();
    }

    @Test
    void upsert_createsANewOverrideWhenNoneExistsYet() {
        Organization organization = Organization.builder().name("Acme").build();
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        when(policyConfigurationRepository.findByOrganizationIdAndRuleId(ORG_ID, "RULE_A")).thenReturn(Optional.empty());
        when(policyConfigurationRepository.save(any(PolicyConfiguration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        PolicyConfigurationResponse response = service.upsert(
                ORG_ID, "RULE_A", new UpdatePolicyConfigurationRequest(false, PolicySeverity.HIGH));

        assertThat(response.enabled()).isFalse();
        assertThat(response.severity()).isEqualTo(PolicySeverity.HIGH);
        assertThat(response.overridden()).isTrue();
    }

    @Test
    void upsert_updatesAnExistingOverrideInPlaceRatherThanCreatingASecondRow() {
        Organization organization = Organization.builder().name("Acme").build();
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.of(organization));
        PolicyConfiguration existing = PolicyConfiguration.builder()
                .organization(organization).ruleId("RULE_A").enabled(false).build();
        when(policyConfigurationRepository.findByOrganizationIdAndRuleId(ORG_ID, "RULE_A")).thenReturn(Optional.of(existing));
        when(policyConfigurationRepository.save(any(PolicyConfiguration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.upsert(ORG_ID, "RULE_A", new UpdatePolicyConfigurationRequest(true, null));

        assertThat(existing.isEnabled()).isTrue();
        assertThat(existing.getSeverityOverride()).isNull();
        verify(policyConfigurationRepository).save(existing);
    }

    @Test
    void upsert_throwsResourceNotFoundForAnUnknownRuleId() {
        assertThatThrownBy(() -> service.upsert(ORG_ID, "NOT_A_REAL_RULE", new UpdatePolicyConfigurationRequest(true, null)))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(policyConfigurationRepository, never()).save(any());
    }

    @Test
    void upsert_throwsResourceNotFoundForAnUnknownOrganization() {
        when(organizationRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(404L, "RULE_A", new UpdatePolicyConfigurationRequest(true, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void resetToDefault_deletesAnExistingOverride() {
        PolicyConfiguration existing = PolicyConfiguration.builder().ruleId("RULE_A").enabled(false).build();
        when(policyConfigurationRepository.findByOrganizationIdAndRuleId(ORG_ID, "RULE_A")).thenReturn(Optional.of(existing));

        PolicyConfigurationResponse response = service.resetToDefault(ORG_ID, "RULE_A");

        verify(policyConfigurationRepository).delete(existing);
        assertThat(response.enabled()).isTrue();
        assertThat(response.overridden()).isFalse();
    }

    @Test
    void resetToDefault_isANoOpWhenNoOverrideExists() {
        when(policyConfigurationRepository.findByOrganizationIdAndRuleId(ORG_ID, "RULE_A")).thenReturn(Optional.empty());

        PolicyConfigurationResponse response = service.resetToDefault(ORG_ID, "RULE_A");

        verify(policyConfigurationRepository, never()).delete(any());
        assertThat(response.overridden()).isFalse();
    }

    @Test
    void resetToDefault_throwsResourceNotFoundForAnUnknownRuleId() {
        assertThatThrownBy(() -> service.resetToDefault(ORG_ID, "NOT_A_REAL_RULE"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private PolicyRule testRule(String id, PolicyCategory category, PolicySeverity severity) {
        return new PolicyRule() {
            public String id() {
                return id;
            }

            public String description() {
                return "test rule " + id;
            }

            public PolicyCategory defaultCategory() {
                return category;
            }

            public PolicySeverity defaultSeverity() {
                return severity;
            }

            public List<PolicyFinding> evaluate(PolicyContext context) {
                return List.of();
            }
        };
    }
}
