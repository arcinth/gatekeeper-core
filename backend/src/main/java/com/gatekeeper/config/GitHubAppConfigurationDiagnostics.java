package com.gatekeeper.config;

import jakarta.annotation.PostConstruct;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Logs whether the GitHub App is fully configured, once, at startup - on
 * every profile, not just prod (GitHubSecretsStartupValidator only runs
 * under prod, and doesn't check app.slug at all).
 * <p>
 * GitHubInstallationController.getInstallUrl() deliberately never tells the
 * frontend *why* appConfigured is false - the App's id/slug never leave the
 * backend - so "The GitHub App is not configured" in the UI is, by design,
 * the last thing a developer sees, not the first. This is the actual
 * diagnostic: it names exactly which environment variable is missing, so
 * that message is never the end of the trail.
 */
@Slf4j
@Component
public class GitHubAppConfigurationDiagnostics {

    // Mirrors application.yml's gatekeeper.github.webhook.secret default and
    // GitHubSecretsStartupValidator.DEFAULT_WEBHOOK_SECRET - update all three together.
    private static final String DEFAULT_WEBHOOK_SECRET =
            "local-development-webhook-secret-change-me-in-production-please";

    private final long appId;
    private final String appSlug;
    private final String privateKey;
    private final String privateKeyPath;
    private final String webhookSecret;

    public GitHubAppConfigurationDiagnostics(
            @Value("${gatekeeper.github.app.id}") long appId,
            @Value("${gatekeeper.github.app.slug:}") String appSlug,
            @Value("${gatekeeper.github.app.private-key}") String privateKey,
            @Value("${gatekeeper.github.app.private-key-path:}") String privateKeyPath,
            @Value("${gatekeeper.github.webhook.secret}") String webhookSecret) {
        this.appId = appId;
        this.appSlug = appSlug;
        this.privateKey = privateKey;
        this.privateKeyPath = privateKeyPath;
        this.webhookSecret = webhookSecret;
    }

    @PostConstruct
    void logStatus() {
        logWebhookSecretDiagnostics();

        List<String> missing = missingProperties();
        if (missing.isEmpty()) {
            log.info("✓ GitHub App configured (App ID, slug, private key, and webhook secret all present)");
        } else {
            log.warn("✗ GitHub App configuration incomplete - missing: {}. "
                    + "\"Connect GitHub\" and webhook/API integration will not work until these are set. "
                    + "GitHub App credentials are optional for local development - everything else works "
                    + "normally without them.",
                    String.join("; ", missing));
        }
    }

    /**
     * Reports presence and length only - never the secret itself. Also the
     * fail-fast check requested after a real debugging session where a webhook
     * silently 401'd for hours: an App ID configured against a webhook secret
     * that is still the committed placeholder can never verify a real GitHub
     * signature, so refusing to start is strictly better than the alternative
     * (every webhook delivery failing with no indication why until someone
     * traces GitHubWebhookController by hand). Deliberately scoped to "appId is
     * set" rather than every profile unconditionally - a developer not touching
     * GitHub integration at all must still be able to start with the default in
     * place, per the existing "GitHub App credentials are optional for local
     * development" contract.
     */
    private void logWebhookSecretDiagnostics() {
        boolean configured = webhookSecret != null && !webhookSecret.isBlank();
        String sanitized = SecretSanitizer.sanitize(webhookSecret);
        log.info("GitHub webhook secret configured: {}. Secret length: {}.",
                configured ? "YES" : "NO", sanitized == null ? 0 : sanitized.length());

        if (configured && !webhookSecret.equals(sanitized)) {
            log.warn("GITHUB_WEBHOOK_SECRET has leading/trailing whitespace or a byte-order mark - sanitized "
                    + "automatically before use, but check where this value came from (a .env file saved with a "
                    + "BOM, or a value copied with a trailing newline, are common causes).");
        }

        if (appId > 0 && DEFAULT_WEBHOOK_SECRET.equals(sanitized)) {
            throw new IllegalStateException(
                    "GITHUB_APP_ID is configured (a real GitHub App is expected to be wired up) but "
                            + "GITHUB_WEBHOOK_SECRET is still the committed placeholder default. Every webhook "
                            + "GitHub sends will fail signature verification until this is set to the secret "
                            + "configured on the App's own webhook settings page. Set GITHUB_WEBHOOK_SECRET in "
                            + ".env (or your shell/IDE run configuration) and restart.");
        }
    }

    List<String> missingProperties() {
        List<String> missing = new ArrayList<>();

        if (appId <= 0) {
            missing.add("GITHUB_APP_ID");
        }
        if (appSlug == null || appSlug.isBlank()) {
            missing.add("GITHUB_APP_SLUG (required for the \"Connect GitHub\" install link - see INSTALLATION.md)");
        }

        boolean privateKeyPresent = (privateKey != null && !privateKey.isBlank())
                || (privateKeyPath != null && !privateKeyPath.isBlank());
        if (!privateKeyPresent) {
            missing.add("GITHUB_APP_PRIVATE_KEY or GITHUB_APP_PRIVATE_KEY_PATH");
        } else if (privateKeyPath != null && !privateKeyPath.isBlank() && !isReadableFile(privateKeyPath)) {
            missing.add("GITHUB_APP_PRIVATE_KEY_PATH ('" + privateKeyPath + "' is not a readable file)");
        }

        if (webhookSecret == null || webhookSecret.isBlank()) {
            missing.add("GITHUB_WEBHOOK_SECRET");
        }

        return missing;
    }

    private static boolean isReadableFile(String path) {
        try {
            return Files.isReadable(Path.of(path));
        } catch (InvalidPathException ex) {
            return false;
        }
    }
}
