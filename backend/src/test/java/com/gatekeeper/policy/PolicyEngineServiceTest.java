package com.gatekeeper.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyEngineServiceTest {

    private final PolicyEngine policyEngine = mock(PolicyEngine.class);
    private final PolicyEngineService service = new PolicyEngineService(policyEngine);

    @Test
    void evaluate_delegatesToThePolicyEngineAndReturnsItsResultUnchanged() {
        PolicyContext context = new PolicyContext(42L, "org/repo", List.of());
        PolicyFinding finding = new PolicyFinding("RULE_A", PolicyCategory.CODE_QUALITY, PolicySeverity.LOW, "f.txt", 1, "msg", "rec");
        PolicyResult expected = new PolicyResult(42L, List.of(finding), 1, Instant.now());
        when(policyEngine.evaluate(context)).thenReturn(expected);

        PolicyResult result = service.evaluate(context);

        assertThat(result).isEqualTo(expected);
        verify(policyEngine).evaluate(context);
    }
}
