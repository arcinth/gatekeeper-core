package com.gatekeeper.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.policyconfiguration.PolicyConfigurationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyEngineServiceTest {

    private final PolicyEngine policyEngine = mock(PolicyEngine.class);
    private final PolicyConfigurationService policyConfigurationService = mock(PolicyConfigurationService.class);
    private final PolicyEngineService service = new PolicyEngineService(policyEngine, policyConfigurationService);

    @Test
    void evaluate_loadsTheOrganizationsConfigurationAndDelegatesToThePolicyEngine() {
        PolicyContext context = new PolicyContext(42L, 7L, "org/repo", List.of());
        PolicyConfigurationSet configuration = PolicyConfigurationSet.EMPTY;
        PolicyFinding finding = new PolicyFinding("RULE_A", PolicyCategory.CODE_QUALITY, PolicySeverity.LOW, "f.txt", 1, "msg", "rec");
        PolicyResult expected = new PolicyResult(42L, List.of(finding), 1, Instant.now());
        when(policyConfigurationService.buildConfigurationSet(7L)).thenReturn(configuration);
        when(policyEngine.evaluate(context, configuration)).thenReturn(expected);

        PolicyResult result = service.evaluate(context);

        assertThat(result).isEqualTo(expected);
        verify(policyEngine).evaluate(context, configuration);
    }
}
