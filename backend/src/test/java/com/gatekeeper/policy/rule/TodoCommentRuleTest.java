package com.gatekeeper.policy.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.policy.PolicyCategory;
import com.gatekeeper.policy.PolicyContext;
import com.gatekeeper.policy.PolicyFinding;
import com.gatekeeper.policy.PolicySeverity;
import java.util.List;
import org.junit.jupiter.api.Test;

class TodoCommentRuleTest {

    private final TodoCommentRule rule = new TodoCommentRule();

    @Test
    void evaluate_findsATodoCommentWithCorrectLineNumberAndMetadata() {
        PolicyContext context = contextWith("src/Foo.java", "class Foo {\n    // TODO: refactor this\n}");

        List<PolicyFinding> findings = rule.evaluate(context);

        assertThat(findings).hasSize(1);
        PolicyFinding finding = findings.get(0);
        assertThat(finding.ruleId()).isEqualTo("TODO_COMMENT");
        assertThat(finding.category()).isEqualTo(PolicyCategory.MAINTAINABILITY);
        assertThat(finding.severity()).isEqualTo(PolicySeverity.LOW);
        assertThat(finding.filePath()).isEqualTo("src/Foo.java");
        assertThat(finding.lineNumber()).isEqualTo(2);
        assertThat(finding.message()).contains("TODO: refactor this");
    }

    @Test
    void evaluate_isCaseInsensitive() {
        PolicyContext context = contextWith("a.txt", "// todo: lowercase marker");

        assertThat(rule.evaluate(context)).hasSize(1);
    }

    @Test
    void evaluate_doesNotFlagTodoAsASubstringOfALongerWord() {
        PolicyContext context = contextWith("a.txt", "int TODOLIST_COUNT = 5;");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_findsMultipleOccurrencesAcrossMultipleFiles() {
        PolicyContext context = new PolicyContext(1L, 1L, "org/repo", List.of(
                new PolicyContext.ChangedFile("a.txt", "// TODO one\nclean line\n// TODO two"),
                new PolicyContext.ChangedFile("b.txt", "// TODO three")));

        List<PolicyFinding> findings = rule.evaluate(context);

        assertThat(findings).hasSize(3);
        assertThat(findings).extracting(PolicyFinding::filePath).containsExactly("a.txt", "a.txt", "b.txt");
        assertThat(findings).extracting(PolicyFinding::lineNumber).containsExactly(1, 3, 1);
    }

    @Test
    void evaluate_returnsEmptyListForCleanContent() {
        PolicyContext context = contextWith("clean.txt", "class Foo {\n    void bar() {}\n}");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_returnsEmptyListWhenThereAreNoChangedFiles() {
        PolicyContext context = new PolicyContext(1L, 1L, "org/repo", List.of());

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_doesNotFlagFixmeMarkers() {
        PolicyContext context = contextWith("a.txt", "// FIXME: this is broken");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    private PolicyContext contextWith(String path, String content) {
        return new PolicyContext(1L, 1L, "org/repo", List.of(new PolicyContext.ChangedFile(path, content)));
    }
}
