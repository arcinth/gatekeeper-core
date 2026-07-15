package com.gatekeeper.securityengine.rule;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Flags construction of java.util.Random or calls to Math.random() - neither
 * is cryptographically secure (both are seeded, statistically-predictable
 * PRNGs), so using either to generate a token, password, session identifier,
 * or nonce is a real weakness. java.security.SecureRandom is the correct
 * choice for anything security-sensitive.
 * <p>
 * Deliberately scoped to just these two constructs, not
 * java.util.concurrent.ThreadLocalRandom: Oracle's own documentation already
 * flags ThreadLocalRandom as unsuitable for security use, but it also has
 * plenty of legitimate non-security uses (e.g. load distribution across
 * threads) that would make flagging every occurrence noisier than the
 * signal is worth for a deterministic, no-heuristics rule.
 * <p>
 * This cannot know whether a given Random usage actually feeds something
 * security-sensitive (a data-flow question, not a pattern-matching one) - the
 * same accepted trade-off HardcodedSecretRule and InsecureCryptoFunctionRule
 * already make: a blunt, deterministic pattern match with known false
 * positives (e.g. Random used for a game or a simulation) rather than a
 * heuristic attempt at context-awareness. Severity is MEDIUM rather than
 * CRITICAL specifically because of that lower confidence.
 */
@Component
public final class InsecureRandomnessRule extends PatternMatchRule {

    private static final String RULE_ID = "INSECURE_RANDOMNESS";
    private static final Pattern INSECURE_RANDOM_PATTERN = Pattern.compile(
            "\\bnew\\s+(java\\.util\\.)?Random\\s*\\(|\\bMath\\.random\\s*\\(");

    public InsecureRandomnessRule() {
        super(INSECURE_RANDOM_PATTERN);
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Flags java.util.Random construction and Math.random() calls, "
                + "neither of which is cryptographically secure.";
    }

    @Override
    protected SecurityFinding buildFinding(String filePath, int lineNumber, String lineContent) {
        return new SecurityFinding(
                RULE_ID,
                SecurityCategory.INSECURE_CRYPTOGRAPHY,
                SecuritySeverity.MEDIUM,
                filePath,
                lineNumber,
                "Insecure random number generator used: " + lineContent,
                "If this value is security-sensitive (a token, password, session ID, or nonce), "
                        + "use java.security.SecureRandom instead.");
    }
}
