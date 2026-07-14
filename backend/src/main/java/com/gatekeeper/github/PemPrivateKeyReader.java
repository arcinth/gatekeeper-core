package com.gatekeeper.github;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Parses a PEM-encoded RSA private key for signing GitHub App JWTs.
 * Only PKCS#8 ("BEGIN PRIVATE KEY") is supported: it's what java.security.KeyFactory
 * can parse without a third-party crypto library. GitHub issues new App keys in
 * PKCS#1 ("BEGIN RSA PRIVATE KEY") by default, so that case gets a specific,
 * actionable error instead of a generic parse failure.
 */
final class PemPrivateKeyReader {

    private static final String PKCS8_HEADER = "-----BEGIN PRIVATE KEY-----";
    private static final String PKCS8_FOOTER = "-----END PRIVATE KEY-----";
    private static final String PKCS1_HEADER = "-----BEGIN RSA PRIVATE KEY-----";

    private PemPrivateKeyReader() {
    }

    static PrivateKey readPkcs8RsaPrivateKey(String pem) {
        String trimmed = pem.trim();

        if (trimmed.contains(PKCS1_HEADER)) {
            throw new IllegalStateException(
                    "GitHub App private key is in PKCS#1 format (\"BEGIN RSA PRIVATE KEY\"), which the JDK "
                            + "cannot parse directly. Convert it to PKCS#8 with: openssl pkcs8 -topk8 -inform PEM "
                            + "-outform PEM -nocrypt -in <downloaded-key>.pem -out <pkcs8-key>.pem");
        }
        if (!trimmed.contains(PKCS8_HEADER)) {
            throw new IllegalStateException(
                    "GitHub App private key does not look like a PEM-encoded PKCS#8 RSA private key "
                            + "(expected a \"-----BEGIN PRIVATE KEY-----\" header).");
        }

        String base64Body = trimmed
                .replace(PKCS8_HEADER, "")
                .replace(PKCS8_FOOTER, "")
                .replaceAll("\\s", "");

        try {
            byte[] decoded = Base64.getDecoder().decode(base64Body);
            PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(decoded);
            return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
        } catch (IllegalArgumentException | GeneralSecurityException ex) {
            throw new IllegalStateException("GitHub App private key could not be parsed as a valid RSA key.", ex);
        }
    }
}
