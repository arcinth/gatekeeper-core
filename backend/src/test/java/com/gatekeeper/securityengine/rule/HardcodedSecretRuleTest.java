package com.gatekeeper.securityengine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityContext;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.util.List;
import org.junit.jupiter.api.Test;

class HardcodedSecretRuleTest {

    private final HardcodedSecretRule rule = new HardcodedSecretRule();

    @Test
    void evaluate_findsAHardcodedPasswordWithCorrectLineNumberAndMetadata() {
        SecurityContext context = contextWith("src/Config.java", "class Config {\n    String password = \"changeme123\";\n}");

        List<SecurityFinding> findings = rule.evaluate(context);

        assertThat(findings).hasSize(1);
        SecurityFinding finding = findings.get(0);
        assertThat(finding.ruleId()).isEqualTo("HARDCODED_SECRET");
        assertThat(finding.category()).isEqualTo(SecurityCategory.SECRETS_EXPOSURE);
        assertThat(finding.severity()).isEqualTo(SecuritySeverity.CRITICAL);
        assertThat(finding.filePath()).isEqualTo("src/Config.java");
        assertThat(finding.lineNumber()).isEqualTo(2);
        assertThat(finding.message()).contains("changeme123");
    }

    @Test
    void evaluate_isCaseInsensitiveOnTheVariableName() {
        SecurityContext context = contextWith("a.txt", "API_KEY = \"sk-abcdef123456\"");

        assertThat(rule.evaluate(context)).hasSize(1);
    }

    @Test
    void evaluate_matchesColonStyleAssignmentToo() {
        SecurityContext context = contextWith("config.yaml", "secret: \"s3cr3t-value\"");

        assertThat(rule.evaluate(context)).hasSize(1);
    }

    @Test
    void evaluate_doesNotFlagAssignmentFromAFunctionCallOrVariable() {
        SecurityContext context = contextWith("a.txt", "String password = getSecretFromVault();");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_doesNotFlagANullOrEmptyAssignment() {
        SecurityContext context = new SecurityContext(1L, "org/repo", List.of(
                new SecurityContext.ChangedFile("a.txt", "String password = null;"),
                new SecurityContext.ChangedFile("b.txt", "String password = \"\";")));

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_findsMultipleOccurrencesAcrossMultipleFiles() {
        SecurityContext context = new SecurityContext(1L, "org/repo", List.of(
                new SecurityContext.ChangedFile("a.txt", "password = \"one23456\"\nclean line\nauthToken = \"two23456\""),
                new SecurityContext.ChangedFile("b.txt", "secret = \"three23456\"")));

        List<SecurityFinding> findings = rule.evaluate(context);

        assertThat(findings).hasSize(3);
        assertThat(findings).extracting(SecurityFinding::filePath).containsExactly("a.txt", "a.txt", "b.txt");
        assertThat(findings).extracting(SecurityFinding::lineNumber).containsExactly(1, 3, 1);
    }

    @Test
    void evaluate_returnsEmptyListForCleanContent() {
        SecurityContext context = contextWith("clean.txt", "class Foo {\n    void bar() {}\n}");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_returnsEmptyListWhenThereAreNoChangedFiles() {
        SecurityContext context = new SecurityContext(1L, "org/repo", List.of());

        assertThat(rule.evaluate(context)).isEmpty();
    }

    private SecurityContext contextWith(String path, String content) {
        return new SecurityContext(1L, "org/repo", List.of(new SecurityContext.ChangedFile(path, content)));
    }
}
