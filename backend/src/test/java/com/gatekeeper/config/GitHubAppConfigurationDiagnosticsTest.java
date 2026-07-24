package com.gatekeeper.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class GitHubAppConfigurationDiagnosticsTest {

    private static final String REAL_PRIVATE_KEY = "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----";
    private static final String REAL_WEBHOOK_SECRET = "a-real-generated-secret-value";
    private static final String DEFAULT_WEBHOOK_SECRET =
            "local-development-webhook-secret-change-me-in-production-please";

    @Test
    void missingProperties_isEmptyWhenEverythingIsConfigured() {
        var diagnostics = new GitHubAppConfigurationDiagnostics(
                42L, "gatekeeper-core", REAL_PRIVATE_KEY, "", REAL_WEBHOOK_SECRET);

        assertThat(diagnostics.missingProperties()).isEmpty();
    }

    @Test
    void missingProperties_flagsAppIdWhenUnset() {
        var diagnostics = new GitHubAppConfigurationDiagnostics(
                0L, "gatekeeper-core", REAL_PRIVATE_KEY, "", REAL_WEBHOOK_SECRET);

        assertThat(diagnostics.missingProperties()).anyMatch(entry -> entry.contains("GITHUB_APP_ID"));
    }

    @Test
    void missingProperties_flagsSlugWhenBlank_evenIfEverythingElseIsSet() {
        var diagnostics = new GitHubAppConfigurationDiagnostics(
                42L, "", REAL_PRIVATE_KEY, "", REAL_WEBHOOK_SECRET);

        assertThat(diagnostics.missingProperties()).anyMatch(entry -> entry.contains("GITHUB_APP_SLUG"));
    }

    @Test
    void missingProperties_flagsSlugWhenNull() {
        var diagnostics = new GitHubAppConfigurationDiagnostics(
                42L, null, REAL_PRIVATE_KEY, "", REAL_WEBHOOK_SECRET);

        assertThat(diagnostics.missingProperties()).anyMatch(entry -> entry.contains("GITHUB_APP_SLUG"));
    }

    @Test
    void missingProperties_flagsPrivateKeyWhenBothKeyAndPathAreBlank() {
        var diagnostics = new GitHubAppConfigurationDiagnostics(
                42L, "gatekeeper-core", "", "", REAL_WEBHOOK_SECRET);

        assertThat(diagnostics.missingProperties()).anyMatch(entry -> entry.contains("GITHUB_APP_PRIVATE_KEY"));
    }

    @Test
    void missingProperties_flagsUnreadablePrivateKeyPath() {
        var diagnostics = new GitHubAppConfigurationDiagnostics(
                42L, "gatekeeper-core", "", "/does/not/exist.pem", REAL_WEBHOOK_SECRET);

        assertThat(diagnostics.missingProperties())
                .anyMatch(entry -> entry.contains("GITHUB_APP_PRIVATE_KEY_PATH") && entry.contains("not a readable file"));
    }

    @Test
    void missingProperties_passesWhenPrivateKeyPathIsSetEvenIfPrivateKeyIsBlank() {
        // Uses this test file itself as a stand-in for a readable path - content doesn't matter here.
        String thisFile = "src/test/java/com/gatekeeper/config/GitHubAppConfigurationDiagnosticsTest.java";
        var diagnostics = new GitHubAppConfigurationDiagnostics(
                42L, "gatekeeper-core", "", thisFile, REAL_WEBHOOK_SECRET);

        assertThat(diagnostics.missingProperties()).noneMatch(entry -> entry.contains("GITHUB_APP_PRIVATE_KEY"));
    }

    @Test
    void missingProperties_flagsWebhookSecretWhenBlank() {
        var diagnostics = new GitHubAppConfigurationDiagnostics(
                42L, "gatekeeper-core", REAL_PRIVATE_KEY, "", "");

        assertThat(diagnostics.missingProperties()).anyMatch(entry -> entry.contains("GITHUB_WEBHOOK_SECRET"));
    }

    @Test
    void logStatus_doesNotThrowRegardlessOfConfigurationState() {
        var configured = new GitHubAppConfigurationDiagnostics(
                42L, "gatekeeper-core", REAL_PRIVATE_KEY, "", REAL_WEBHOOK_SECRET);
        var unconfigured = new GitHubAppConfigurationDiagnostics(0L, "", "", "", "");

        configured.logStatus();
        unconfigured.logStatus();
    }

    @Test
    void logStatus_throwsWhenAppIdIsConfiguredButWebhookSecretIsStillTheDefaultPlaceholder() {
        var diagnostics = new GitHubAppConfigurationDiagnostics(
                42L, "gatekeeper-core", REAL_PRIVATE_KEY, "", DEFAULT_WEBHOOK_SECRET);

        assertThatThrownBy(diagnostics::logStatus)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GITHUB_WEBHOOK_SECRET");
    }

    @Test
    void logStatus_throwsWhenTheDefaultPlaceholderIsOnlyPollutedByWhitespace() {
        // Sanitization must happen before the default-value comparison - a
        // trailing newline on the placeholder itself must not accidentally
        // slip past the check that is supposed to catch exactly this value.
        var diagnostics = new GitHubAppConfigurationDiagnostics(
                42L, "gatekeeper-core", REAL_PRIVATE_KEY, "", DEFAULT_WEBHOOK_SECRET + "\n");

        assertThatThrownBy(diagnostics::logStatus).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void logStatus_doesNotThrowForTheDefaultPlaceholderWhenAppIdIsNotConfigured() {
        // The "GitHub App credentials are optional for local development"
        // contract: a developer not touching GitHub integration at all must
        // still be able to start with every default left in place.
        var diagnostics = new GitHubAppConfigurationDiagnostics(
                0L, "", REAL_PRIVATE_KEY, "", DEFAULT_WEBHOOK_SECRET);

        assertThatCode(diagnostics::logStatus).doesNotThrowAnyException();
    }
}
