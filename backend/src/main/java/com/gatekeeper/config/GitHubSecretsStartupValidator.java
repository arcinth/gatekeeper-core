package com.gatekeeper.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails startup under the prod profile if the GitHub App is not properly
 * configured. A separate class from JwtSecretStartupValidator rather than a
 * generalized "secret validator" - Sprint 1 is frozen, and these three checks
 * (a default-value comparison, a blank check, and an unset-sentinel check)
 * don't share enough structure to abstract without forcing an awkward shape
 * onto the existing, already-tested JWT validator.
 * Scoped to @Profile("prod") only: this bean does not exist under local/dev.
 */
@Component
@Profile("prod")
public class GitHubSecretsStartupValidator {

    // Mirrors application.yml's gatekeeper.github.webhook.secret default - update both together.
    private static final String DEFAULT_WEBHOOK_SECRET =
            "local-development-webhook-secret-change-me-in-production-please";

    private final String webhookSecret;
    private final String appPrivateKey;
    private final long appId;

    public GitHubSecretsStartupValidator(
            @Value("${gatekeeper.github.webhook.secret}") String webhookSecret,
            @Value("${gatekeeper.github.app.private-key}") String appPrivateKey,
            @Value("${gatekeeper.github.app.id}") long appId) {
        this.webhookSecret = webhookSecret;
        this.appPrivateKey = appPrivateKey;
        this.appId = appId;
    }

    @PostConstruct
    public void validate() {
        if (DEFAULT_WEBHOOK_SECRET.equals(webhookSecret)) {
            throw new IllegalStateException(
                    "Refusing to start under the 'prod' profile: GITHUB_WEBHOOK_SECRET is still set to its "
                            + "default development value. Set it to a unique, securely generated secret.");
        }
        if (appPrivateKey == null || appPrivateKey.isBlank()) {
            throw new IllegalStateException(
                    "Refusing to start under the 'prod' profile: GITHUB_APP_PRIVATE_KEY is not set.");
        }
        if (appId <= 0) {
            throw new IllegalStateException(
                    "Refusing to start under the 'prod' profile: GITHUB_APP_ID is not set.");
        }
    }
}
