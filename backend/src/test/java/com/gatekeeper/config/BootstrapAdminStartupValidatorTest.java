package com.gatekeeper.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BootstrapAdminStartupValidatorTest {

    private static final String DEFAULT_BOOTSTRAP_ADMIN_PASSWORD = "ChangeMe123!";
    private static final String REAL_BOOTSTRAP_ADMIN_PASSWORD = "a-real-generated-password-value";

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
}
