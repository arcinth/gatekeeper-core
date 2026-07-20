package com.gatekeeper.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecretStrengthValidatorTest {

    @Test
    void requireNotWeak_rejectsAValueContainingAKnownPlaceholderWord() {
        assertThatThrownBy(() -> SecretStrengthValidator.requireNotWeak("TEST_SECRET", "MyAdminPassword"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("TEST_SECRET");
    }

    @Test
    void requireNotWeak_matchIsCaseInsensitive() {
        assertThatThrownBy(() -> SecretStrengthValidator.requireNotWeak("TEST_SECRET", "SECRET123"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requireNotWeak_acceptsAValueWithNoWeakSubstring() {
        assertThatCode(() -> SecretStrengthValidator.requireNotWeak("TEST_SECRET", "kx9-Qz2-vR7-mN4-wL8-pT1-hF6"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireNotWeak_toleratesANullValue() {
        assertThatCode(() -> SecretStrengthValidator.requireNotWeak("TEST_SECRET", null)).doesNotThrowAnyException();
    }

    @Test
    void requireMinimumLength_rejectsAValueShorterThanTheMinimum() {
        assertThatThrownBy(() -> SecretStrengthValidator.requireMinimumLength("TEST_SECRET", "short", 32))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    @Test
    void requireMinimumLength_acceptsAValueAtExactlyTheMinimum() {
        assertThatCode(() -> SecretStrengthValidator.requireMinimumLength("TEST_SECRET", "12345678", 8))
                .doesNotThrowAnyException();
    }
}
