package com.gatekeeper.securityengine.rule;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Flags a secret-shaped variable (password, api key, token, ...) assigned a
 * literal quoted string. Deterministic pattern match only - no entropy
 * analysis, no allow-list of known-safe placeholders - consistent with the
 * "no heuristics" design principle. Requires an immediately-following quoted
 * literal, not just the variable name, so `password = getSecret()` and
 * `password = null` are not flagged - only a literal value committed in
 * plain text is what this rule exists to catch.
 */
@Component
public final class HardcodedSecretRule extends PatternMatchRule {

    private static final String RULE_ID = "HARDCODED_SECRET";
    private static final Pattern SECRET_ASSIGNMENT_PATTERN = Pattern.compile(
            "(?i)\\b(password|passwd|api[_-]?key|secret|access[_-]?key|auth[_-]?token|private[_-]?key)\\b"
                    + "\\s*[:=]\\s*[\"']([^\"']{3,})[\"']");

    public HardcodedSecretRule() {
        super(SECRET_ASSIGNMENT_PATTERN);
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Flags secret-shaped variables (password, api key, token, ...) assigned a literal string value.";
    }

    @Override
    protected SecurityFinding buildFinding(String filePath, int lineNumber, String lineContent) {
        return new SecurityFinding(
                RULE_ID,
                SecurityCategory.SECRETS_EXPOSURE,
                SecuritySeverity.CRITICAL,
                filePath,
                lineNumber,
                "Possible hardcoded secret found: " + lineContent,
                "Remove the literal value and load it from a secrets manager or environment variable instead.");
    }
}
