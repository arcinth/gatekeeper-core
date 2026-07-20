package com.gatekeeper.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtSecretStartupValidatorTest {

    private static final String STRONG_SECRET = "kx9QzVr7mN4wL8pT1hF6bY3dC5jK0sA2eU9gI7oP4x";

    @Test
    void validate_rejectsTheExactCommittedDefault() {
        var validator = new JwtSecretStartupValidator(
                "local-development-secret-key-change-me-in-production-please-0123456789");

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_rejectsAValueContainingAWeakPlaceholderWord() {
        var validator = new JwtSecretStartupValidator("my-test-secret-padding-padding-padding-padding");

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_rejectsAValueShorterThan32Characters() {
        var validator = new JwtSecretStartupValidator("short-key");

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_acceptsAStrongUniqueSecret() {
        var validator = new JwtSecretStartupValidator(STRONG_SECRET);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
