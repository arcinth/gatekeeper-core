package com.gatekeeper.securityengine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Proves what a unit test structurally cannot: that Spring's real component
 * scan discovers every {@code @Component} SecurityRule and wires it into
 * SecurityEngine with no manual registration anywhere - mirrors
 * com.gatekeeper.policy.PolicyEngineIntegrationTest exactly. SecurityEngineTest
 * exercises the engine's own logic against hand-built SecurityRule instances;
 * this test exists solely to verify the *discovery* mechanism, which only a
 * real ApplicationContext can demonstrate.
 * <p>
 * Scoped to a @ComponentScan of just the securityengine package - not a full
 * @SpringBootTest - so this stays fast and independent of the datasource,
 * matching this milestone's "no persistence, no orchestration wiring yet" scope.
 */
@SpringJUnitConfig(SecurityEngineIntegrationTest.SecurityEnginePackageConfig.class)
class SecurityEngineIntegrationTest {

    @Configuration
    @ComponentScan(basePackages = "com.gatekeeper.securityengine")
    static class SecurityEnginePackageConfig {
    }

    @Autowired
    private SecurityEngine securityEngine;

    @Autowired
    private SecurityEngineService securityEngineService;

    @Autowired
    private List<SecurityRule> discoveredRules;

    @Test
    void springDiscoversEveryRuleWithoutAnyManualRegistration() {
        assertThat(discoveredRules)
                .extracting(SecurityRule::id)
                .containsExactlyInAnyOrder("HARDCODED_SECRET", "INSECURE_CRYPTO_FUNCTION",
                        "AWS_ACCESS_KEY", "GITHUB_PAT", "INSECURE_RANDOMNESS");
    }

    @Test
    void engineEndToEnd_aggregatesFindingsFromAllDiscoveredRulesForAMixedFile() {
        SecurityContext context = new SecurityContext(100L, "gatekeeper/core", List.of(
                new SecurityContext.ChangedFile(
                        "src/main/java/Example.java",
                        "class Example {\n"
                                + "    String password = \"changeme123\";\n"
                                + "    MessageDigest md = MessageDigest.getInstance(\"MD5\");\n"
                                + "    String awsKey = \"AKIAIOSFODNN7EXAMPLE\";\n"
                                + "    String ghToken = \"ghp_a1B2c3D4e5F6g7H8i9J0k1L2m3N4o5P6q7R8\";\n"
                                + "    Random random = new Random();\n"
                                + "}")));

        SecurityResult result = securityEngine.evaluate(context);

        assertThat(result.rulesEvaluated()).isEqualTo(5);
        assertThat(result.findings()).hasSize(5);
        assertThat(result.findings())
                .extracting(SecurityFinding::ruleId)
                .containsExactlyInAnyOrder("HARDCODED_SECRET", "INSECURE_CRYPTO_FUNCTION",
                        "AWS_ACCESS_KEY", "GITHUB_PAT", "INSECURE_RANDOMNESS");
    }

    @Test
    void serviceEndToEnd_delegatesThroughTheRealSpringWiredEngine() {
        SecurityContext context = new SecurityContext(101L, "gatekeeper/core", List.of(
                new SecurityContext.ChangedFile("a.txt", "apiKey = \"sk-abcdef123456\"")));

        SecurityResult result = securityEngineService.evaluate(context);

        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).ruleId()).isEqualTo("HARDCODED_SECRET");
    }

    @Test
    void engineEndToEnd_returnsCleanResultWhenNoPatternsArePresent() {
        SecurityContext context = new SecurityContext(102L, "gatekeeper/core", List.of(
                new SecurityContext.ChangedFile("clean.txt", "class Clean {\n    void ok() {}\n}")));

        SecurityResult result = securityEngine.evaluate(context);

        assertThat(result.hasFindings()).isFalse();
        assertThat(result.rulesEvaluated()).isEqualTo(5);
    }
}
