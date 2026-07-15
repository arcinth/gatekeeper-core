package com.gatekeeper.policy.rule;

import com.gatekeeper.policy.PolicyContext;
import com.gatekeeper.policy.PolicyFinding;
import com.gatekeeper.policy.PolicyRule;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Shared line-scanning logic for rules that flag a single textual marker
 * (TODO, FIXME, ...) anywhere in a changed file. Package-private: it's an
 * implementation detail shared by this package's rules, not part of the
 * public PolicyRule extension surface - a rule checking something structural
 * rather than textual (e.g. file size, import cycles) would implement
 * PolicyRule directly instead of extending this.
 * <p>
 * Stateless and thread-safe: the only field is an immutable, precompiled
 * Pattern, satisfying the statelessness contract PolicyRule documents.
 */
abstract class CommentMarkerRule implements PolicyRule {

    private final Pattern markerPattern;

    protected CommentMarkerRule(Pattern markerPattern) {
        this.markerPattern = markerPattern;
    }

    @Override
    public List<PolicyFinding> evaluate(PolicyContext context) {
        List<PolicyFinding> findings = new ArrayList<>();
        for (PolicyContext.ChangedFile file : context.changedFiles()) {
            findings.addAll(scanFile(file));
        }
        return findings;
    }

    private List<PolicyFinding> scanFile(PolicyContext.ChangedFile file) {
        List<PolicyFinding> findings = new ArrayList<>();
        String[] lines = file.content().split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (markerPattern.matcher(lines[i]).find()) {
                findings.add(buildFinding(file.path(), i + 1, lines[i].trim()));
            }
        }
        return findings;
    }

    /** Builds the finding for one matched line; lineContent is already trimmed. */
    protected abstract PolicyFinding buildFinding(String filePath, int lineNumber, String lineContent);
}
