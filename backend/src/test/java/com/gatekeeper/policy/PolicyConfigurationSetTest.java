package com.gatekeeper.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PolicyConfigurationSetTest {

    @Test
    void isEnabled_defaultsToTrueForARuleIdWithNoOpinion() {
        assertThat(PolicyConfigurationSet.EMPTY.isEnabled("ANY_RULE")).isTrue();
    }

    @Test
    void isEnabled_reflectsAnExplicitFalseEntry() {
        PolicyConfigurationSet configuration = new PolicyConfigurationSet(Map.of("RULE_A", false), Map.of());

        assertThat(configuration.isEnabled("RULE_A")).isFalse();
        assertThat(configuration.isEnabled("RULE_B")).isTrue();
    }

    @Test
    void severityOverride_isEmptyForARuleIdWithNoOverride() {
        assertThat(PolicyConfigurationSet.EMPTY.severityOverride("ANY_RULE")).isEmpty();
    }

    @Test
    void severityOverride_reflectsAnExplicitEntry() {
        PolicyConfigurationSet configuration = new PolicyConfigurationSet(Map.of(), Map.of("RULE_A", PolicySeverity.CRITICAL));

        assertThat(configuration.severityOverride("RULE_A")).contains(PolicySeverity.CRITICAL);
        assertThat(configuration.severityOverride("RULE_B")).isEmpty();
    }

    @Test
    void constructor_defensivelyCopiesTheSuppliedMapsSoLaterMutationCannotChangeAnAlreadyBuiltSnapshot() {
        Map<String, Boolean> enabledByRuleId = new HashMap<>(Map.of("RULE_A", true));
        PolicyConfigurationSet configuration = new PolicyConfigurationSet(enabledByRuleId, Map.of());

        enabledByRuleId.put("RULE_A", false);

        assertThat(configuration.isEnabled("RULE_A")).isTrue();
    }

    @Test
    void constructor_rejectsANullKeyOrValueRatherThanSilentlyAcceptingIt() {
        Map<String, Boolean> withNullValue = new HashMap<>();
        withNullValue.put("RULE_A", null);

        assertThatThrownBy(() -> new PolicyConfigurationSet(withNullValue, Map.of()))
                .isInstanceOf(NullPointerException.class);
    }
}
