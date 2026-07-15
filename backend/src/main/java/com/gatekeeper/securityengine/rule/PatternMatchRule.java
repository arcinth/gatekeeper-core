package com.gatekeeper.securityengine.rule;

import com.gatekeeper.securityengine.SecurityContext;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecurityRule;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared line-scanning logic for rules that flag a single textual pattern
 * (a secret-like assignment, a weak crypto algorithm name, ...) anywhere in a
 * changed file. Package-private: it's an implementation detail shared by this
 * package's rules, not part of the public SecurityRule extension surface -
 * mirrors com.gatekeeper.policy.rule.CommentMarkerRule exactly, one level
 * deeper than a textual marker (an arbitrary regex rather than a single word).
 * <p>
 * Stateless and thread-safe: the only field is an immutable, precompiled
 * Pattern, satisfying the statelessness contract SecurityRule documents.
 */
abstract class PatternMatchRule implements SecurityRule {

    private final Pattern matchPattern;

    protected PatternMatchRule(Pattern matchPattern) {
        this.matchPattern = matchPattern;
    }

    @Override
    public List<SecurityFinding> evaluate(SecurityContext context) {
        List<SecurityFinding> findings = new ArrayList<>();
        for (SecurityContext.ChangedFile file : context.changedFiles()) {
            findings.addAll(scanFile(file));
        }
        return findings;
    }

    private List<SecurityFinding> scanFile(SecurityContext.ChangedFile file) {
        List<SecurityFinding> findings = new ArrayList<>();
        String[] lines = file.content().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (matchPattern.matcher(lines[i]).find()) {
                findings.add(buildFinding(file.path(), i + 1, lines[i].trim()));
            }
        }
        return findings;
    }

    /** Builds the finding for one matched line; lineContent is already trimmed. */
    protected abstract SecurityFinding buildFinding(String filePath, int lineNumber, String lineContent);
}
