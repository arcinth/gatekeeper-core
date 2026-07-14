package com.gatekeeper.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GitHubSecretsStartupValidatorTest {

    private static final String DEFAULT_WEBHOOK_SECRET =
            "local-development-webhook-secret-change-me-in-production-please";
    private static final String REAL_WEBHOOK_SECRET = "a-real-generated-secret-value";
    private static final String REAL_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----";

    @Test
    void validate_throwsWhenWebhookSecretIsStillTheDefault() {
        var validator = new GitHubSecretsStartupValidator(DEFAULT_WEBHOOK_SECRET, REAL_PRIVATE_KEY, 42L);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GITHUB_WEBHOOK_SECRET");
    }

    @Test
    void validate_throwsWhenPrivateKeyIsBlank() {
        var validator = new GitHubSecretsStartupValidator(REAL_WEBHOOK_SECRET, "", 42L);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GITHUB_APP_PRIVATE_KEY");
    }

    @Test
    void validate_throwsWhenPrivateKeyIsNull() {
        var validator = new GitHubSecretsStartupValidator(REAL_WEBHOOK_SECRET, null, 42L);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GITHUB_APP_PRIVATE_KEY");
    }

    @Test
    void validate_throwsWhenAppIdIsUnset() {
        var validator = new GitHubSecretsStartupValidator(REAL_WEBHOOK_SECRET, REAL_PRIVATE_KEY, 0L);

        assertThatThrownBy(validator::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GITHUB_APP_ID");
    }

    @Test
    void validate_passesWhenEverythingIsProperlyConfigured() {
        var validator = new GitHubSecretsStartupValidator(REAL_WEBHOOK_SECRET, REAL_PRIVATE_KEY, 42L);

        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
}
