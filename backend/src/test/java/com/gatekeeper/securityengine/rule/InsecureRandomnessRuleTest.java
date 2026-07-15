package com.gatekeeper.securityengine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityContext;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.util.List;
import org.junit.jupiter.api.Test;

class InsecureRandomnessRuleTest {

    private final InsecureRandomnessRule rule = new InsecureRandomnessRule();

    @Test
    void evaluate_findsNewRandomWithCorrectLineNumberAndMetadata() {
        SecurityContext context = contextWith("src/TokenGenerator.java",
                "class TokenGenerator {\n    Random random = new Random();\n}");

        List<SecurityFinding> findings = rule.evaluate(context);

        assertThat(findings).hasSize(1);
        SecurityFinding finding = findings.get(0);
        assertThat(finding.ruleId()).isEqualTo("INSECURE_RANDOMNESS");
        assertThat(finding.category()).isEqualTo(SecurityCategory.INSECURE_CRYPTOGRAPHY);
        assertThat(finding.severity()).isEqualTo(SecuritySeverity.MEDIUM);
        assertThat(finding.filePath()).isEqualTo("src/TokenGenerator.java");
        assertThat(finding.lineNumber()).isEqualTo(2);
    }

    @Test
    void evaluate_findsTheFullyQualifiedFormToo() {
        SecurityContext context = contextWith("a.txt", "java.util.Random r = new java.util.Random();");

        assertThat(rule.evaluate(context)).hasSize(1);
    }

    @Test
    void evaluate_findsMathRandomCalls() {
        SecurityContext context = contextWith("a.txt", "double d = Math.random();");

        assertThat(rule.evaluate(context)).hasSize(1);
    }

    @Test
    void evaluate_toleratesExtraWhitespaceBeforeTheParenthesis() {
        SecurityContext context = contextWith("a.txt", "Random r = new Random  ();");

        assertThat(rule.evaluate(context)).hasSize(1);
    }

    @Test
    void evaluate_doesNotFlagSecureRandom() {
        SecurityContext context = contextWith("a.txt", "SecureRandom r = new SecureRandom();");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_doesNotFlagUsageOfAnAlreadyConstructedRandomInstance() {
        // A data-flow limitation this rule deliberately accepts, mirroring the same
        // trade-off HardcodedSecretRule and InsecureCryptoFunctionRule already make.
        SecurityContext context = contextWith("a.txt", "int n = random.nextInt(100);");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_doesNotFlagAnUnrelatedMathMethod() {
        SecurityContext context = contextWith("a.txt", "double d = Math.sqrt(4);");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_findsMultipleOccurrencesAcrossMultipleFiles() {
        SecurityContext context = new SecurityContext(1L, "org/repo", List.of(
                new SecurityContext.ChangedFile("a.txt", "new Random()\nclean line\nMath.random()"),
                new SecurityContext.ChangedFile("b.txt", "new java.util.Random()")));

        List<SecurityFinding> findings = rule.evaluate(context);

        assertThat(findings).hasSize(3);
        assertThat(findings).extracting(SecurityFinding::filePath).containsExactly("a.txt", "a.txt", "b.txt");
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
