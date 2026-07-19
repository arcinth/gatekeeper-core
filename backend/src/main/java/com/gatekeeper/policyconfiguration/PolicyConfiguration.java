package com.gatekeeper.policyconfiguration;

import com.gatekeeper.common.BaseEntity;
import com.gatekeeper.organization.Organization;
import com.gatekeeper.policy.PolicySeverity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One organization's override of one code-defined {@link com.gatekeeper.policy.PolicyRule}
 * (Milestone 6: Policy Management). This table stores ONLY the override -
 * never the rule catalog itself (id/description/default category/default
 * severity stay in code; see docs/Policy-Development.md). A row's absence
 * for a given (organization, ruleId) pair means "use that rule's own
 * default" - see {@link com.gatekeeper.policy.PolicyConfigurationSet}.
 * <p>
 * Extends BaseEntity (has updatedAt), unlike the write-once entities
 * elsewhere in this codebase (Verdict, PolicyFindingEntity): an
 * administrator is expected to toggle enabled/severityOverride repeatedly,
 * and doing so never touches or retroactively changes any already-persisted
 * PolicyFinding - only future evaluations see a changed configuration.
 * <p>
 * ruleId is a plain String, not a foreign key - there is no rule table to
 * reference. PolicyEngine fails fast at startup if two PolicyRule beans ever
 * share an id, which is what keeps this column's values meaningful.
 */
@Getter
@Setter
@Entity
@Table(name = "policy_configurations")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyConfiguration extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "rule_id", nullable = false, length = 100)
    private String ruleId;

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity_override", length = 20)
    private PolicySeverity severityOverride;
}
