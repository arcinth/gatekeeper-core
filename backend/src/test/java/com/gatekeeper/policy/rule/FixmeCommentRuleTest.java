package com.gatekeeper.policy.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicyContext;
import com.gatekeeper.policy.PolicyFinding;
import com.gatekeeper.policy.PolicySeverity;
import java.util.List;
import org.junit.jupiter.api.Test;

class FixmeCommentRuleTest {

    private final FixmeCommentRule rule = new FixmeCommentRule();

    @Test
    void evaluate_findsAFixmeCommentWithCorrectLineNumberAndMetadata() {
        PolicyContext context = contextWith("src/Bar.java", "class Bar {\n    // FIXME: null check missing\n}");

        List<PolicyFinding> findings = rule.evaluate(context);

        assertThat(findings).hasSize(1);
        PolicyFinding finding = findings.get(0);
        assertThat(finding.ruleId()).isEqualTo("FIXME_COMMENT");
        assertThat(finding.category()).isEqualTo(PolicyCategory.CODE_QUALITY);
        assertThat(finding.severity()).isEqualTo(PolicySeverity.MEDIUM);
        assertThat(finding.filePath()).isEqualTo("src/Bar.java");
        assertThat(finding.lineNumber()).isEqualTo(2);
        assertThat(finding.message()).contains("FIXME: null check missing");
    }

    @Test
    void evaluate_isCaseInsensitive() {
        PolicyContext context = contextWith("a.txt", "// fixme: lowercase marker");

        assertThat(rule.evaluate(context)).hasSize(1);
    }

    @Test
    void evaluate_doesNotFlagFixmeAsASubstringOfALongerWord() {
        PolicyContext context = contextWith("a.txt", "int FIXMENOW_COUNT = 5;");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_returnsEmptyListForCleanContent() {
        PolicyContext context = contextWith("clean.txt", "class Foo {\n    void bar() {}\n}");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_doesNotFlagTodoMarkers() {
        PolicyContext context = contextWith("a.txt", "// TODO: revisit later");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    private PolicyContext contextWith(String path, String content) {
        return new PolicyContext(1L, "org/repo", List.of(new PolicyContext.ChangedFile(path, content)));
    }
}
