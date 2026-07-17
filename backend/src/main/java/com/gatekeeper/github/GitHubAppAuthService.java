package com.gatekeeper.github;

import com.gatekeeper.github.dto.InstallationAccessTokenResponse;
import io.jsonwebtoken.Jwts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Mints GitHub App JWTs and exchanges them for per-installation access tokens
 * (Architecture.md: this and GitHubApiClient are the only components that ever
 * see GitHub credentials - see ADR-011 in the Sprint 2 design).
 *
 * Installation tokens are cached in memory only, keyed by installation ID, and
 * refreshed a few minutes before their real expiry so a request never races a
 * token that GitHub is about to consider expired.
 */
@Service
public class GitHubAppAuthService {

    // GitHub rejects App JWTs with a lifetime over 10 minutes.
    private static final Duration APP_JWT_TTL = Duration.ofMinutes(10);
    // GitHub's documented tolerance for clock drift between us and their servers.
    private static final Duration APP_JWT_CLOCK_SKEW_TOLERANCE = Duration.ofSeconds(60);
    // Refresh before actual expiry so an in-flight request never presents an
    // installation token that GitHub considers expired by the time it arrives.
    private static final Duration TOKEN_REFRESH_SAFETY_MARGIN = Duration.ofMinutes(5);

    private final GitHubApiClient gitHubApiClient;
    private final Clock clock;
    private final long appId;
    private final String privateKeyPem;

    private final ConcurrentHashMap<Long, CachedInstallationToken> installationTokenCache = new ConcurrentHashMap<>();

    public GitHubAppAuthService(
            GitHubApiClient gitHubApiClient,
            Clock clock,
            @Value("${gatekeeper.github.app.id}") long appId,
            @Value("${gatekeeper.github.app.private-key}") String privateKeyPem,
            @Value("${gatekeeper.github.app.private-key-path:}") String privateKeyPath) {
        this.gitHubApiClient = gitHubApiClient;
        this.clock = clock;
        this.appId = appId;
        this.privateKeyPem = resolvePrivateKeyPem(privateKeyPem, privateKeyPath);
    }

    /**
     * private-key-path wins when set - reading the PEM file once at startup,
     * the same "resolve once, fail fast if broken" approach as the rest of
     * this project's startup-time config (see GitHubSecretsStartupValidator).
     * Falls back to the raw private-key value otherwise, so existing
     * deployments and tests that set GITHUB_APP_PRIVATE_KEY directly are
     * unaffected.
     */
    private static String resolvePrivateKeyPem(String privateKeyPem, String privateKeyPath) {
        if (privateKeyPath == null || privateKeyPath.isBlank()) {
            return privateKeyPem;
        }
        try {
            return Files.readString(Path.of(privateKeyPath));
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "GITHUB_APP_PRIVATE_KEY_PATH is set to '" + privateKeyPath
                            + "' but the file could not be read.", ex);
        }
    }

    /**
     * Returns a cached installation access token if one is still valid, otherwise
     * mints an App JWT and exchanges it for a fresh token via GitHubApiClient.
     */
    public String getInstallationAccessToken(long installationId) {
        CachedInstallationToken cached = installationTokenCache.get(installationId);
        if (cached != null && cached.isValidAt(clock.instant())) {
            return cached.token();
        }
        return fetchAndCacheInstallationToken(installationId);
    }

    private String fetchAndCacheInstallationToken(long installationId) {
        // compute() locks only this installation's cache entry, so refreshing one
        // installation's token never blocks a concurrent request for another.
        CachedInstallationToken token = installationTokenCache.compute(installationId, (id, existing) -> {
            if (existing != null && existing.isValidAt(clock.instant())) {
                return existing;
            }
            InstallationAccessTokenResponse response = gitHubApiClient.mintInstallationAccessToken(id, mintAppJwt());
            return new CachedInstallationToken(response.token(), response.expiresAt());
        });
        return token.token();
    }

    String mintAppJwt() {
        if (privateKeyPem == null || privateKeyPem.isBlank()) {
            throw new IllegalStateException(
                    "GitHub App private key is not configured. Set GITHUB_APP_PRIVATE_KEY.");
        }

        PrivateKey privateKey = PemPrivateKeyReader.readPkcs8RsaPrivateKey(privateKeyPem);
        Instant issuedAt = clock.instant().minus(APP_JWT_CLOCK_SKEW_TOLERANCE);
        Instant expiresAt = issuedAt.plus(APP_JWT_TTL);

        return Jwts.builder()
                .setIssuer(String.valueOf(appId))
                .setIssuedAt(Date.from(issuedAt))
                .setExpiration(Date.from(expiresAt))
                .signWith(privateKey)
                .compact();
    }

    private record CachedInstallationToken(String token, Instant expiresAt) {
        boolean isValidAt(Instant now) {
            return now.isBefore(expiresAt.minus(TOKEN_REFRESH_SAFETY_MARGIN));
        }
    }
}
