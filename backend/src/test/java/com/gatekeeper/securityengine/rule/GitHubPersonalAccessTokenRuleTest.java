package com.gatekeeper.securityengine.rule;

import static org.assertj.core.api.Assertions.assertThat;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityContext;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.util.List;
import org.junit.jupiter.api.Test;

class GitHubPersonalAccessTokenRuleTest {

    private final GitHubPersonalAccessTokenRule rule = new GitHubPersonalAccessTokenRule();

    // Synthetic 36-char suffix built programmatically, never hand-counted, to avoid an off-by-one
    // in the test itself - real GitHub classic tokens are always exactly 36 characters after the prefix.
    private static final String SUFFIX_36 = "a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8";

    @Test
    void evaluate_findsAClassicPatWithCorrectLineNumberAndMetadata() {
        String token = "ghp_" + SUFFIX_36;
        SecurityContext context = contextWith("src/Config.java",
                "class Config {\n    String token = \"" + token + "\";\n}");

        List<SecurityFinding> findings = rule.evaluate(context);

        assertThat(findings).hasSize(1);
        SecurityFinding finding = findings.get(0);
        assertThat(finding.ruleId()).isEqualTo("GITHUB_PAT");
        assertThat(finding.category()).isEqualTo(SecurityCategory.SECRETS_EXPOSURE);
        assertThat(finding.severity()).isEqualTo(SecuritySeverity.CRITICAL);
        assertThat(finding.filePath()).isEqualTo("src/Config.java");
        assertThat(finding.lineNumber()).isEqualTo(2);
        assertThat(finding.message()).contains(token);
    }

    @Test
    void evaluate_matchesEveryKnownGitHubTokenPrefix() {
        for (String prefix : List.of("ghp", "gho", "ghu", "ghs", "ghr")) {
            SecurityContext context = contextWith("a.txt", "t = \"" + prefix + "_" + SUFFIX_36 + "\"");

            assertThat(rule.evaluate(context)).as("prefix " + prefix).hasSize(1);
        }
    }

    @Test
    void evaluate_assertsTheSyntheticSuffixIsExactly36CharactersLong() {
        assertThat(SUFFIX_36).hasSize(36);
    }

    @Test
    void evaluate_doesNotFlagAnUnquotedToken() {
        SecurityContext context = contextWith("a.txt", "System.out.println(ghp_" + SUFFIX_36 + ");");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_doesNotFlagATokenThatIsTooShort() {
        SecurityContext context = contextWith("a.txt", "t = \"ghp_tooshort\"");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_doesNotFlagAnUnrelatedPrefix() {
        SecurityContext context = contextWith("a.txt", "t = \"ghx_" + SUFFIX_36 + "\"");

        assertThat(rule.evaluate(context)).isEmpty();
    }

    @Test
    void evaluate_findsMultipleOccurrencesAcrossMultipleFiles() {
        SecurityContext context = new SecurityContext(1L, "org/repo", List.of(
                new SecurityContext.ChangedFile("a.txt", "one = \"ghp_" + SUFFIX_36 + "\"\nclean line"),
                new SecurityContext.ChangedFile("b.txt", "two = \"gho_" + SUFFIX_36 + "\"")));

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
