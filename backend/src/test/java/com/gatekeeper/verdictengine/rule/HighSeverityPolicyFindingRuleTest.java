package com.gatekeeper.verdictengine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicyFinding;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.verdictengine.VerdictContext;
import com.gatekeeper.verdictengine.VerdictReason;
import java.util.List;
import org.junit.jupiter.api.Test;

class HighSeverityPolicyFindingRuleTest {

    private final HighSeverityPolicyFindingRule rule = new HighSeverityPolicyFindingRule();

    @Test
    void evaluate_producesABlockingReasonForAHighSeverityPolicyFinding() {
        PolicyFinding finding = policyFinding(PolicySeverity.HIGH);
        VerdictContext context = contextWith(List.of(finding));

        List<VerdictReason> reasons = rule.evaluate(context);

        assertThat(reasons).hasSize(1);
        assertThat(reasons.get(0).ruleId()).isEqualTo("HIGH_SEVERITY_POLICY_FINDING");
        assertThat(reasons.get(0).blocking()).isTrue();
        assertThat(reasons.get(0).message())
                .contains("HIGH")
                .contains("FIXME_COMMENT")
                .contains("src/Foo.java:7");
    }

    @Test
    void evaluate_alsoBlocksOnCriticalSeverityEvenThoughTheRuleIsNamedForHigh() {
        // PolicySeverity is documented as "ordered low to high" specifically so a
        // threshold rule can use >= comparison - CRITICAL is a superset of "high severity".
        VerdictContext context = contextWith(List.of(policyFinding(PolicySeverity.CRITICAL)));

        List<VerdictReason> reasons = rule.evaluate(context);

        assertThat(reasons).hasSize(1);
        assertThat(reasons.get(0).blocking()).isTrue();
    }

    @Test
    void evaluate_producesOneReasonPerQualifyingFindingWhenThereAreMultiple() {
        VerdictContext context = contextWith(
                List.of(policyFinding(PolicySeverity.HIGH), policyFinding(PolicySeverity.CRITICAL)));

        List<VerdictReason> reasons = rule.evaluate(context);

        assertThat(reasons).hasSize(2);
        assertThat(reasons).allSatisfy(reason -> assertThat(reason.blocking()).isTrue());
    }

    @Test
    void evaluate_ignoresLowerSeverities() {
        VerdictContext context = contextWith(
                List.of(policyFinding(PolicySeverity.LOW), policyFinding(PolicySeverity.MEDIUM)));

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_returnsEmptyListWhenThereAreNoPolicyFindings() {
        assertThat(rule.evaluate(contextWith(List.of()))).isEmpty();
    }

    @Test
    void id_returnsTheStableRuleId() {
        assertThat(rule.id()).isEqualTo("HIGH_SEVERITY_POLICY_FINDING");
    }

    private PolicyFinding policyFinding(PolicySeverity severity) {
        return new PolicyFinding("FIXME_COMMENT", PolicyCategory.CODE_QUALITY, severity,
                "src/Foo.java", 7, "Known-defect marker left in code", "Resolve or remove the FIXME.");
    }

    private VerdictContext contextWith(List<PolicyFinding> policyFindings) {
        return new VerdictContext(1L, "org/repo", policyFindings, List.of());
    }
}
