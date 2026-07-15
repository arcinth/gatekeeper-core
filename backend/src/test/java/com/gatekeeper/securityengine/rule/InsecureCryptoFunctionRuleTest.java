package com.gatekeeper.securityengine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityContext;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.util.List;
import org.junit.jupiter.api.Test;

class InsecureCryptoFunctionRuleTest {

    private final InsecureCryptoFunctionRule rule = new InsecureCryptoFunctionRule();

    @Test
    void evaluate_findsMd5UsageWithCorrectLineNumberAndMetadata() {
        SecurityContext context = contextWith("src/Hasher.java",
                "class Hasher {\n    MessageDigest md = MessageDigest.getInstance(\"MD5\");\n}");

        List<SecurityFinding> findings = rule.evaluate(context);

        assertThat(findings).hasSize(1);
        SecurityFinding finding = findings.get(0);
        assertThat(finding.ruleId()).isEqualTo("INSECURE_CRYPTO_FUNCTION");
        assertThat(finding.category()).isEqualTo(SecurityCategory.INSECURE_CRYPTOGRAPHY);
        assertThat(finding.severity()).isEqualTo(SecuritySeverity.HIGH);
        assertThat(finding.filePath()).isEqualTo("src/Hasher.java");
        assertThat(finding.lineNumber()).isEqualTo(2);
    }

    @Test
    void evaluate_isCaseInsensitiveAndAcceptsSingleQuotes() {
        SecurityContext context = contextWith("a.js", "crypto.createHash('md5')");

        assertThat(rule.evaluate(context)).hasSize(1);
    }

    @Test
    void evaluate_matchesDesRc4Sha1AndEcb() {
        SecurityContext context = new SecurityContext(1L, "org/repo", List.of(
                new SecurityContext.ChangedFile("a.txt", "Cipher.getInstance(\"DES\")"),
                new SecurityContext.ChangedFile("b.txt", "Cipher.getInstance(\"RC4\")"),
                new SecurityContext.ChangedFile("c.txt", "MessageDigest.getInstance(\"SHA1\")"),
                new SecurityContext.ChangedFile("d.txt", "Cipher.getInstance(\"AES/ECB/PKCS5Padding\")")));

        assertThat(rule.evaluate(context)).hasSize(4);
    }

    @Test
    void evaluate_doesNotFlagTheAlgorithmNameWhenItIsNotQuoted() {
        SecurityContext context = contextWith("a.txt", "// This class replaces the old MD5 based hasher.");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_doesNotFlagAStrongAlgorithm() {
        SecurityContext context = contextWith("a.txt", "MessageDigest.getInstance(\"SHA-256\")");

        assertThat(rule.evaluate(context)).isEmpty();
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
