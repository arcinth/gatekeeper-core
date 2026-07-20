package com.gatekeeper.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BootstrapAdminStartupValidatorTest {

    private static final String DEFAULT_BOOTSTRAP_ADMIN_PASSWORD = "ChangeMe123!";
    // Milestone 10: Security Hardening's new weak-pattern check (see SecretStrengthValidator)
    // rejects any value containing "password" as a substring - this fixture was renamed from
    // "a-real-generated-password-value" for exactly that reason, not to weaken the check.
    private static final String REAL_BOOTSTRAP_ADMIN_PASSWORD = "a-real-generated-secure-value-99";

    @Test
    void validate_throwsWhenBootstrapAdminPasswordIsStillTheDefault() {
        var validator = new BootstrapAdminStartupValidator(DEFAULT_BOOTSTRAP_ADMIN_PASSWORD);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BOOTSTRAP_ADMIN_PASSWORD");
    }

    @Test
    void validate_passesWhenBootstrapAdminPasswordHasBeenOverridden() {
        var validator = new BootstrapAdminStartupValidator(REAL_BOOTSTRAP_ADMIN_PASSWORD);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    /** Milestone 10: Security Hardening - extends this validator beyond the exact-default check above. */
    @Test
    void validate_throwsWhenPasswordContainsAWeakPlaceholderWord() {
        var validator = new BootstrapAdminStartupValidator("SuperAdminPassword2026");

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void validate_throwsWhenPasswordIsShorterThan12Characters() {
        var validator = new BootstrapAdminStartupValidator("Kx9!zVr7mN");

        assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }
}
