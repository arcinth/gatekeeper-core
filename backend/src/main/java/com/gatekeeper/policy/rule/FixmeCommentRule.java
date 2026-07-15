package com.gatekeeper.policy.rule;

import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicyFinding;
import com.gatekeeper.policy.PolicySeverity;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Flags FIXME markers. Medium severity and CODE_QUALITY, not
 * MAINTAINABILITY: a FIXME signals a known-broken state rather than
 * incomplete-but-working code, which is what distinguishes it from
 * TodoCommentRule and justifies the higher severity.
 */
@Component
public final class FixmeCommentRule extends CommentMarkerRule {

    private static final String RULE_ID = "FIXME_COMMENT";
    private static final Pattern FIXME_PATTERN = Pattern.compile("\\bFIXME\\b", Pattern.CASE_INSENSITIVE);

    public FixmeCommentRule() {
        super(FIXME_PATTERN);
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Flags FIXME markers left in changed files.";
    }

    @Override
    protected PolicyFinding buildFinding(String filePath, int lineNumber, String lineContent) {
        return new PolicyFinding(
                RULE_ID,
                PolicyCategory.CODE_QUALITY,
                PolicySeverity.MEDIUM,
                filePath,
                lineNumber,
                "FIXME comment found: " + lineContent,
                "This marks a known defect - fix it or explain why it's safe to defer before merging.");
    }
}
