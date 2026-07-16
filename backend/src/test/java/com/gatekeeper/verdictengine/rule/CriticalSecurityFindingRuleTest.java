package com.gatekeeper.verdictengine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.verdictengine.VerdictContext;
import com.gatekeeper.verdictengine.VerdictReason;
import java.util.List;
import org.junit.jupiter.api.Test;

class CriticalSecurityFindingRuleTest {

    private final CriticalSecurityFindingRule rule = new CriticalSecurityFindingRule();

    @Test
    void evaluate_producesABlockingReasonForACriticalSecurityFinding() {
        SecurityFinding finding = securityFinding(SecuritySeverity.CRITICAL);
        VerdictContext context = contextWith(List.of(finding));

        List<VerdictReason> reasons = rule.evaluate(context);

        assertThat(reasons).hasSize(1);
        assertThat(reasons.get(0).ruleId()).isEqualTo("CRITICAL_SECURITY_FINDING");
        assertThat(reasons.get(0).blocking()).isTrue();
        assertThat(reasons.get(0).message())
                .contains("HARDCODED_SECRET")
                .contains("src/Config.java:1")
                .contains("Possible hardcoded secret");
    }

    @Test
    void evaluate_producesOneReasonPerQualifyingFindingWhenThereAreMultiple() {
        VerdictContext context = contextWith(List.of(
                securityFinding(SecuritySeverity.CRITICAL), securityFinding(SecuritySeverity.CRITICAL)));

        List<VerdictReason> reasons = rule.evaluate(context);

        assertThat(reasons).hasSize(2);
        assertThat(reasons).allSatisfy(reason -> assertThat(reason.blocking()).isTrue());
    }

    @Test
    void evaluate_ignoresNonCriticalSeverities() {
        VerdictContext context = contextWith(List.of(
                securityFinding(SecuritySeverity.LOW),
                securityFinding(SecuritySeverity.MEDIUM),
                securityFinding(SecuritySeverity.HIGH)));

        List<VerdictReason> reasons = rule.evaluate(context);

        assertThat(reasons).isEmpty();
    }

    @Test
    void evaluate_returnsEmptyListWhenThereAreNoSecurityFindings() {
        VerdictContext context = contextWith(List.of());

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void id_returnsTheStableRuleId() {
        assertThat(rule.id()).isEqualTo("CRITICAL_SECURITY_FINDING");
    }

    private SecurityFinding securityFinding(SecuritySeverity severity) {
        return new SecurityFinding("HARDCODED_SECRET", SecurityCategory.SECRETS_EXPOSURE, severity,
                "src/Config.java", 1, "Possible hardcoded secret found", "Remove the literal value.");
    }

    private VerdictContext contextWith(List<SecurityFinding> securityFindings) {
        return new VerdictContext(1L, "org/repo", List.of(), securityFindings);
    }
}
