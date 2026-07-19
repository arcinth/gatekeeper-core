package com.gatekeeper.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.gatekeeper.policyconfiguration.PolicyConfigurationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Proves what a unit test structurally cannot: that Spring's real component
 * scan discovers every {@code @Component} PolicyRule and wires it into
 * PolicyEngine with no manual registration anywhere. PolicyEngineTest
 * exercises the engine's own logic against hand-built PolicyRule instances;
 * this test exists solely to verify the *discovery* mechanism, which only a
 * real ApplicationContext can demonstrate.
 * <p>
 * Scoped to a @ComponentScan of just the policy package - not a full
 * @SpringBootTest - so this stays fast and independent of the datasource,
 * matching the rest of this milestone's "no persistence yet" scope.
 * PolicyConfigurationService (Milestone 6) is provided as a hand-mocked
 * @Bean rather than the real, JPA-backed implementation, for the same
 * reason - PolicyEngineService needs one to construct, but this test has no
 * interest in policy configuration persistence, only rule discovery.
 */
@SpringJUnitConfig(PolicyEngineIntegrationTest.PolicyPackageConfig.class)
class PolicyEngineIntegrationTest {

    @Configuration
    @ComponentScan(basePackages = "com.gatekeeper.policy")
    static class PolicyPackageConfig {

        @Bean
        PolicyConfigurationService policyConfigurationService() {
            PolicyConfigurationService mock = mock(PolicyConfigurationService.class);
            when(mock.buildConfigurationSet(org.mockito.ArgumentMatchers.anyLong())).thenReturn(PolicyConfigurationSet.EMPTY);
            return mock;
        }
    }

    @Autowired
    private PolicyEngine policyEngine;

    @Autowired
    private PolicyEngineService policyEngineService;

    @Autowired
    private List<PolicyRule> discoveredRules;

    @Test
    void springDiscoversBothDemonstrationRulesWithoutAnyManualRegistration() {
        assertThat(discoveredRules)
                .extracting(PolicyRule::id)
                .containsExactlyInAnyOrder("TODO_COMMENT", "FIXME_COMMENT");
    }

    @Test
    void engineEndToEnd_aggregatesFindingsFromAllDiscoveredRulesForAMixedFile() {
        PolicyContext context = new PolicyContext(100L, 1L, "gatekeeper/core", List.of(
                new PolicyContext.ChangedFile(
                        "src/main/java/Example.java",
                        "class Example {\n"
                                + "    // TODO: extract this into a helper\n"
                                + "    void run() {\n"
                                + "        // FIXME: null check missing here\n"
                                + "    }\n"
                                + "}")));

        PolicyResult result = policyEngine.evaluate(context, PolicyConfigurationSet.EMPTY);

        assertThat(result.rulesEvaluated()).isEqualTo(2);
        assertThat(result.findings()).hasSize(2);
        assertThat(result.findings())
                .extracting(PolicyFinding::ruleId)
                .containsExactlyInAnyOrder("TODO_COMMENT", "FIXME_COMMENT");
    }

    @Test
    void serviceEndToEnd_delegatesThroughTheRealSpringWiredEngine() {
        PolicyContext context = new PolicyContext(101L, 1L, "gatekeeper/core", List.of(
                new PolicyContext.ChangedFile("a.txt", "// TODO: wire this up")));

        PolicyResult result = policyEngineService.evaluate(context);

        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).ruleId()).isEqualTo("TODO_COMMENT");
    }

    @Test
    void engineEndToEnd_returnsCleanResultWhenNoMarkersArePresent() {
        PolicyContext context = new PolicyContext(102L, 1L, "gatekeeper/core", List.of(
                new PolicyContext.ChangedFile("clean.txt", "class Clean {\n    void ok() {}\n}")));

        PolicyResult result = policyEngine.evaluate(context, PolicyConfigurationSet.EMPTY);

        assertThat(result.hasFindings()).isFalse();
        assertThat(result.rulesEvaluated()).isEqualTo(2);
    }
}
