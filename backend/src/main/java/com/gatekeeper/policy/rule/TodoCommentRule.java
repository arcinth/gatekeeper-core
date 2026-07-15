package com.gatekeeper.policy.rule;

import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicyFinding;
import com.gatekeeper.policy.PolicySeverity;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Flags unresolved TODO markers. Low severity and MAINTAINABILITY, not
 * CODE_QUALITY: a TODO signals planned-but-incomplete work rather than a
 * known defect, which is what distinguishes it from FixmeCommentRule.
 */
@Component
public final class TodoCommentRule extends CommentMarkerRule {

    private static final String RULE_ID = "TODO_COMMENT";
    private static final Pattern TODO_PATTERN = Pattern.compile("\\bTODO\\b", Pattern.CASE_INSENSITIVE);

    public TodoCommentRule() {
        super(TODO_PATTERN);
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Flags TODO markers left in changed files.";
    }

    @Override
    protected PolicyFinding buildFinding(String filePath, int lineNumber, String lineContent) {
        return new PolicyFinding(
                RULE_ID,
                PolicyCategory.MAINTAINABILITY,
                PolicySeverity.LOW,
                filePath,
                lineNumber,
                "TODO comment found: " + lineContent,
                "Resolve this TODO or link it to a tracked issue before merging.");
    }
}
