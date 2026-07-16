package com.gatekeeper.verdictengine.rule;

import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import com.gatekeeper.verdictengine.VerdictContext;
import com.gatekeeper.verdictengine.VerdictReason;
import com.gatekeeper.verdictengine.VerdictRule;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Blocks the merge if any SecurityFinding has SecuritySeverity.CRITICAL.
 * A single CRITICAL security finding (a hardcoded secret, for example) is an
 * unconditional blocker - severe enough that no count or threshold matters,
 * unlike HighSeverityPolicyFindingRule's "HIGH-or-worse" range check.
 * <p>
 * One VerdictReason is produced per qualifying finding, not one aggregate
 * reason, so each reason stays traceable to a specific file and line without
 * needing a foreign key back to the finding row (Sprint 5 Architecture,
 * ADR-040) - the message is self-contained.
 */
@Component
public final class CriticalSecurityFindingRule implements VerdictRule {

    private static final String RULE_ID = "CRITICAL_SECURITY_FINDING";

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Blocks the merge when any Security Engine finding has CRITICAL severity.";
    }

    @Override
    public List<VerdictReason> evaluate(VerdictContext context) {
        return context.securityFindings().stream()
                .filter(finding -> finding.severity() == SecuritySeverity.CRITICAL)
                .map(this::toReason)
                .toList();
    }

    private VerdictReason toReason(SecurityFinding finding) {
        String message = "CRITICAL security finding '%s' in %s:%d - %s".formatted(
                finding.ruleId(), finding.filePath(), finding.lineNumber(), finding.message());
        return new VerdictReason(RULE_ID, message, true);
    }
}
