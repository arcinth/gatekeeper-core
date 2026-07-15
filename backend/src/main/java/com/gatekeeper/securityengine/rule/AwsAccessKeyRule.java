package com.gatekeeper.securityengine.rule;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Flags AWS Access Key IDs by their distinctive, self-identifying format -
 * a 4-character type prefix (AKIA for long-term IAM user keys, ASIA for
 * temporary STS credentials, and the less common AGPA/AIDA/AROA/AIPA/ANPA/
 * ANVA/A3T[A-Z0-9] prefixes for other principal types) followed by exactly 16
 * uppercase alphanumeric characters, matching the well-known pattern used by
 * GitLeaks/TruffleHog-style secret scanners.
 * <p>
 * Unlike HardcodedSecretRule, this does not require a suggestive variable
 * name - the key format itself is specific enough (20 fixed-case characters
 * with a constrained prefix) to be a strong deterministic signal on its own,
 * so this also catches a key embedded in a URL, config string, or oddly-named
 * variable that HardcodedSecretRule's keyword requirement would miss.
 * Case-sensitive and quote-bounded (the whole quoted literal must be exactly
 * the key) since real AWS key IDs are always uppercase and are never a
 * sub-fragment of a longer quoted string in practice.
 */
@Component
public final class AwsAccessKeyRule extends PatternMatchRule {

    private static final String RULE_ID = "AWS_ACCESS_KEY";
    private static final Pattern AWS_ACCESS_KEY_PATTERN = Pattern.compile(
            "[\"'](A3T[A-Z0-9]|AKIA|AGPA|AIDA|AROA|AIPA|ANPA|ANVA|ASIA)[A-Z0-9]{16}[\"']");

    public AwsAccessKeyRule() {
        super(AWS_ACCESS_KEY_PATTERN);
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Flags AWS Access Key IDs (AKIA/ASIA/... prefix + 16 uppercase alphanumeric characters).";
    }

    @Override
    protected SecurityFinding buildFinding(String filePath, int lineNumber, String lineContent) {
        return new SecurityFinding(
                RULE_ID,
                SecurityCategory.SECRETS_EXPOSURE,
                SecuritySeverity.CRITICAL,
                filePath,
                lineNumber,
                "Possible AWS Access Key ID found: " + lineContent,
                "Revoke this key immediately in the AWS IAM console, then load credentials "
                        + "from an environment variable, instance role, or secrets manager instead.");
    }
}
