package com.gatekeeper.securityengine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityContext;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.util.List;
import org.junit.jupiter.api.Test;

class AwsAccessKeyRuleTest {

    private final AwsAccessKeyRule rule = new AwsAccessKeyRule();

    // AWS's own publicly-documented example Access Key ID from their docs - not a real credential.
    private static final String EXAMPLE_KEY = "AKIAIOSFODNN7EXAMPLE";

    @Test
    void evaluate_findsAnAwsAccessKeyWithCorrectLineNumberAndMetadata() {
        SecurityContext context = contextWith("src/Config.java",
                "class Config {\n    String accessKey = \"" + EXAMPLE_KEY + "\";\n}");

        List<SecurityFinding> findings = rule.evaluate(context);

        assertThat(findings).hasSize(1);
        SecurityFinding finding = findings.get(0);
        assertThat(finding.ruleId()).isEqualTo("AWS_ACCESS_KEY");
        assertThat(finding.category()).isEqualTo(SecurityCategory.SECRETS_EXPOSURE);
        assertThat(finding.severity()).isEqualTo(SecuritySeverity.CRITICAL);
        assertThat(finding.filePath()).isEqualTo("src/Config.java");
        assertThat(finding.lineNumber()).isEqualTo(2);
        assertThat(finding.message()).contains(EXAMPLE_KEY);
    }

    @Test
    void evaluate_matchesTheAsiaPrefixForTemporaryStsCredentials() {
        SecurityContext context = contextWith("a.txt", "token = \"ASIAABCDEFGHIJKLMNOP\"");

        assertThat(rule.evaluate(context)).hasSize(1);
    }

    @Test
    void evaluate_isCaseSensitiveAndDoesNotMatchALowercaseKey() {
        SecurityContext context = contextWith("a.txt", "key = \"" + EXAMPLE_KEY.toLowerCase() + "\"");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_doesNotFlagAnUnquotedKey() {
        SecurityContext context = contextWith("a.txt", "System.out.println(" + EXAMPLE_KEY + ");");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_doesNotFlagAStringOfTheWrongLength() {
        SecurityContext context = contextWith("a.txt", "key = \"AKIASHORT\"");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_doesNotRequireASuggestiveVariableName() {
        // Unlike HardcodedSecretRule, the key format alone is the signal - no keyword needed.
        SecurityContext context = contextWith("a.txt", "String x = \"" + EXAMPLE_KEY + "\";");

        assertThat(rule.evaluate(context)).hasSize(1);
    }

    @Test
    void evaluate_findsMultipleOccurrencesAcrossMultipleFiles() {
        SecurityContext context = new SecurityContext(1L, "org/repo", List.of(
                new SecurityContext.ChangedFile("a.txt", "one = \"" + EXAMPLE_KEY + "\"\nclean line"),
                new SecurityContext.ChangedFile("b.txt", "two = \"ASIAABCDEFGHIJKLMNOP\"")));

        List<SecurityFinding> findings = rule.evaluate(context);

        assertThat(findings).hasSize(2);
        assertThat(findings).extracting(SecurityFinding::filePath).containsExactly("a.txt", "b.txt");
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
