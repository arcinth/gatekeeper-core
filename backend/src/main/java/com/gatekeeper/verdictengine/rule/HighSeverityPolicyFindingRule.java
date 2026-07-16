package com.gatekeeper.verdictengine.rule;

import com.gatekeeper.policy.PolicyFinding;
import com.gatekeeper.policy.PolicySeverity;
import com.gatekeeper.verdictengine.VerdictContext;
import com.gatekeeper.verdictengine.VerdictReason;
import com.gatekeeper.verdictengine.VerdictRule;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Blocks the merge if any PolicyFinding has severity HIGH or CRITICAL.
 * Uses an ordinal threshold ({@code >= HIGH}), not exact equality,
 * deliberately: PolicySeverity's own Javadoc documents it as "ordered low to
 * high" for exactly this future-thresholding purpose, and excluding a
 * CRITICAL policy finding from a rule named for "high severity" would be a
 * surprising gap - CRITICAL is, definitionally, also high severity.
 * <p>
 * One VerdictReason is produced per qualifying finding, mirroring
 * CriticalSecurityFindingRule's same per-finding traceability reasoning.
 */
@Component
public final class HighSeverityPolicyFindingRule implements VerdictRule {

    private static final String RULE_ID = "HIGH_SEVERITY_POLICY_FINDING";

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Blocks the merge when any Policy Engine finding has HIGH or CRITICAL severity.";
    }

    @Override
    public List<VerdictReason> evaluate(VerdictContext context) {
        return context.policyFindings().stream()
                .filter(finding -> finding.severity().ordinal() >= PolicySeverity.HIGH.ordinal())
                .map(this::toReason)
                .toList();
    }

    private VerdictReason toReason(PolicyFinding finding) {
        String message = "%s policy finding '%s' in %s:%d - %s".formatted(
                finding.severity(), finding.ruleId(), finding.filePath(), finding.lineNumber(), finding.message());
        return new VerdictReason(RULE_ID, message, true);
    }
}
