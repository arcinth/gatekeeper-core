package com.gatekeeper.securityengine.rule;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Flags GitHub Personal Access Tokens and related token types by their
 * distinctive prefix - ghp_ (classic PAT), gho_ (OAuth token), ghu_ (GitHub
 * App user-to-server token), ghs_ (GitHub App server-to-server token), or
 * ghr_ (refresh token) - followed by exactly 36 alphanumeric characters, the
 * fixed-length format GitHub has used for these tokens since 2021.
 * <p>
 * Scoped to this classic 40-character format only; GitHub's newer
 * fine-grained PATs (github_pat_ prefix, 82 trailing characters) are not
 * covered by this rule - a reasonable follow-up rule, not built here to keep
 * this one's pattern simple and unambiguous. Same format-only matching
 * rationale as AwsAccessKeyRule: no variable-name context required, since the
 * prefix + fixed length is already a strong, low-false-positive signal.
 */
@Component
public final class GitHubPersonalAccessTokenRule extends PatternMatchRule {

    private static final String RULE_ID = "GITHUB_PAT";
    private static final Pattern GITHUB_TOKEN_PATTERN = Pattern.compile(
            "[\"'](ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9]{36}[\"']");

    public GitHubPersonalAccessTokenRule() {
        super(GITHUB_TOKEN_PATTERN);
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Flags GitHub tokens (ghp_/gho_/ghu_/ghs_/ghr_ prefix + 36 alphanumeric characters).";
    }

    @Override
    protected SecurityFinding buildFinding(String filePath, int lineNumber, String lineContent) {
        return new SecurityFinding(
                RULE_ID,
                SecurityCategory.SECRETS_EXPOSURE,
                SecuritySeverity.CRITICAL,
                filePath,
                lineNumber,
                "Possible GitHub token found: " + lineContent,
                "Revoke this token immediately in GitHub Settings > Developer settings, "
                        + "then load it from an environment variable or secrets manager instead.");
    }
}
