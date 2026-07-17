package com.gatekeeper.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gatekeeper.github.dto.InstallationAccessTokenResponse;
import com.gatekeeper.github.exception.GitHubApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GitHubAppAuthServiceTest {

    private static final long APP_ID = 12345L;
    private static final long INSTALLATION_ID = 987L;

    private GitHubApiClient gitHubApiClient;
    private MutableClock clock;
    private KeyPair testKeyPair;
    private GitHubAppAuthService service;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        testKeyPair = generateTestRsaKeyPair();
        gitHubApiClient = mock(GitHubApiClient.class);
        clock = new MutableClock(Instant.parse("2026-07-14T12:00:00Z"));
        service = new GitHubAppAuthService(gitHubApiClient, clock, APP_ID, toPkcs8Pem(testKeyPair.getPrivate()), "");
    }

    @Test
    void mintAppJwt_isSignedByTheConfiguredKeyWithExpectedClaims() {
        String jwt = service.mintAppJwt();

        // The parser must use the same notion of "now" as the service's injected
        // Clock - otherwise, since the token's exp is computed from that fixed
        // test instant rather than the real wall clock, jjwt's default (real-time)
        // expiration check would eventually see it as expired.
        Claims claims = Jwts.parser()
                .setSigningKey(testKeyPair.getPublic())
                .setClock(() -> Date.from(clock.instant()))
                .build()
                .parseClaimsJws(jwt)
                .getBody();

        assertThat(claims.getIssuer()).isEqualTo(String.valueOf(APP_ID));
        // Issued-at is backdated 60s to tolerate clock drift with GitHub's servers.
        assertThat(claims.getIssuedAt().toInstant()).isEqualTo(clock.instant().minusSeconds(60));
        assertThat(claims.getExpiration().toInstant()).isEqualTo(clock.instant().minusSeconds(60).plus(Duration.ofMinutes(10)));
    }

    @Test
    void mintAppJwt_throwsActionableErrorWhenPrivateKeyIsBlank() {
        GitHubAppAuthService withoutKey = new GitHubAppAuthService(gitHubApiClient, clock, APP_ID, "", "");

        assertThatThrownBy(withoutKey::mintAppJwt)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GITHUB_APP_PRIVATE_KEY");
    }

    @Test
    void mintAppJwt_throwsActionableErrorForPkcs1FormattedKey() {
        String pkcs1Pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIBOgIBAAJBAK...\n-----END RSA PRIVATE KEY-----";
        GitHubAppAuthService withPkcs1Key = new GitHubAppAuthService(gitHubApiClient, clock, APP_ID, pkcs1Pem, "");

        assertThatThrownBy(withPkcs1Key::mintAppJwt)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("openssl pkcs8");
    }

    @Test
    void constructor_readsThePrivateKeyFromPrivateKeyPathWhenSet() throws Exception {
        java.nio.file.Path keyFile = java.nio.file.Files.createTempFile("gatekeeper-test-key", ".pem");
        try {
            java.nio.file.Files.writeString(keyFile, toPkcs8Pem(testKeyPair.getPrivate()));
            GitHubAppAuthService withKeyFromPath =
                    new GitHubAppAuthService(gitHubApiClient, clock, APP_ID, "", keyFile.toString());

            String jwt = withKeyFromPath.mintAppJwt();

            Claims claims = Jwts.parser()
                    .setSigningKey(testKeyPair.getPublic())
                    .setClock(() -> Date.from(clock.instant()))
                    .build()
                    .parseClaimsJws(jwt)
                    .getBody();
            assertThat(claims.getIssuer()).isEqualTo(String.valueOf(APP_ID));
        } finally {
            java.nio.file.Files.deleteIfExists(keyFile);
        }
    }

    @Test
    void constructor_throwsActionableErrorWhenPrivateKeyPathDoesNotExist() {
        assertThatThrownBy(() -> new GitHubAppAuthService(
                gitHubApiClient, clock, APP_ID, "", "/no/such/file/github-app-private-key.pem"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GITHUB_APP_PRIVATE_KEY_PATH");
    }

    @Test
    void getInstallationAccessToken_reusesCachedTokenWithinItsValidity() {
        when(gitHubApiClient.mintInstallationAccessToken(eq(INSTALLATION_ID), anyString()))
                .thenReturn(new InstallationAccessTokenResponse("ghs_token", clock.instant().plus(Duration.ofHours(1))));

        String first = service.getInstallationAccessToken(INSTALLATION_ID);
        String second = service.getInstallationAccessToken(INSTALLATION_ID);

        assertThat(first).isEqualTo("ghs_token");
        assertThat(second).isEqualTo("ghs_token");
        verify(gitHubApiClient, times(1)).mintInstallationAccessToken(eq(INSTALLATION_ID), anyString());
    }

    @Test
    void getInstallationAccessToken_refetchesOnceCachedTokenEntersRefreshSafetyMargin() {
        when(gitHubApiClient.mintInstallationAccessToken(eq(INSTALLATION_ID), anyString()))
                .thenReturn(new InstallationAccessTokenResponse("first-token", clock.instant().plus(Duration.ofMinutes(10))))
                .thenReturn(new InstallationAccessTokenResponse("second-token", clock.instant().plus(Duration.ofHours(1))));

        String first = service.getInstallationAccessToken(INSTALLATION_ID);
        // first-token expires in 10 minutes; advancing 6 minutes puts it inside the
        // 5-minute refresh safety margin, so the next call must fetch a new one.
        clock.advance(Duration.ofMinutes(6));
        String second = service.getInstallationAccessToken(INSTALLATION_ID);

        assertThat(first).isEqualTo("first-token");
        assertThat(second).isEqualTo("second-token");
        verify(gitHubApiClient, times(2)).mintInstallationAccessToken(eq(INSTALLATION_ID), anyString());
    }

    @Test
    void getInstallationAccessToken_propagatesGitHubApiExceptionOnFailure() {
        when(gitHubApiClient.mintInstallationAccessToken(eq(INSTALLATION_ID), anyString()))
                .thenThrow(new GitHubApiException("GitHub returned 401"));

        assertThatThrownBy(() -> service.getInstallationAccessToken(INSTALLATION_ID))
                .isInstanceOf(GitHubApiException.class);
    }

    @Test
    void getInstallationAccessToken_cachesTokensSeparatelyPerInstallation() {
        long otherInstallationId = 555L;
        when(gitHubApiClient.mintInstallationAccessToken(eq(INSTALLATION_ID), anyString()))
                .thenReturn(new InstallationAccessTokenResponse("token-a", clock.instant().plus(Duration.ofHours(1))));
        when(gitHubApiClient.mintInstallationAccessToken(eq(otherInstallationId), anyString()))
                .thenReturn(new InstallationAccessTokenResponse("token-b", clock.instant().plus(Duration.ofHours(1))));

        assertThat(service.getInstallationAccessToken(INSTALLATION_ID)).isEqualTo("token-a");
        assertThat(service.getInstallationAccessToken(otherInstallationId)).isEqualTo("token-b");
        verify(gitHubApiClient, times(1)).mintInstallationAccessToken(eq(INSTALLATION_ID), anyString());
        verify(gitHubApiClient, times(1)).mintInstallationAccessToken(eq(otherInstallationId), anyString());
    }

    private static KeyPair generateTestRsaKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String toPkcs8Pem(java.security.PrivateKey privateKey) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }

    /** A Clock whose instant can be advanced mid-test, to exercise expiry logic deterministically. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("Not needed for tests.");
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
