package com.gatekeeper.securityengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class SecurityEngineServiceTest {

    private final SecurityEngine securityEngine = mock(SecurityEngine.class);
    private final SecurityEngineService service = new SecurityEngineService(securityEngine);

    @Test
    void evaluate_delegatesToTheSecurityEngineAndReturnsItsResultUnchanged() {
        SecurityContext context = new SecurityContext(42L, "org/repo", List.of());
        SecurityFinding finding = new SecurityFinding(
                "RULE_A", SecurityCategory.SECRETS_EXPOSURE, SecuritySeverity.LOW, "f.txt", 1, "msg", "rec");
        SecurityResult expected = new SecurityResult(42L, List.of(finding), 1, Instant.now());
        when(securityEngine.evaluate(context)).thenReturn(expected);

        SecurityResult result = service.evaluate(context);

        assertThat(result).isEqualTo(expected);
        verify(securityEngine).evaluate(context);
    }
}
