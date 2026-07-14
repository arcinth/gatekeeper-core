package com.gatekeeper.github;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class PemPrivateKeyReaderTest {

    @Test
    void readPkcs8RsaPrivateKey_parsesAValidPkcs8Key() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String pem = toPkcs8Pem(keyPair.getPrivate());

        PrivateKey parsed = PemPrivateKeyReader.readPkcs8RsaPrivateKey(pem);

        assertThat(parsed.getAlgorithm()).isEqualTo("RSA");
        assertThat(parsed.getEncoded()).isEqualTo(keyPair.getPrivate().getEncoded());
    }

    @Test
    void readPkcs8RsaPrivateKey_toleratesSurroundingWhitespace() throws Exception {
        KeyPair keyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String pem = "\n\n  " + toPkcs8Pem(keyPair.getPrivate()) + "  \n\n";

        assertThat(PemPrivateKeyReader.readPkcs8RsaPrivateKey(pem)).isNotNull();
    }

    @Test
    void readPkcs8RsaPrivateKey_rejectsPkcs1FormatWithActionableMessage() {
        String pkcs1Pem = "-----BEGIN RSA PRIVATE KEY-----\nMIIBOgIBAAJBAK...\n-----END RSA PRIVATE KEY-----";

        assertThatThrownBy(() -> PemPrivateKeyReader.readPkcs8RsaPrivateKey(pkcs1Pem))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PKCS#1")
                .hasMessageContaining("openssl pkcs8");
    }

    @Test
    void readPkcs8RsaPrivateKey_rejectsTextWithNoRecognizedPemHeader() {
        assertThatThrownBy(() -> PemPrivateKeyReader.readPkcs8RsaPrivateKey("not a pem key at all"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PKCS#8");
    }

    @Test
    void readPkcs8RsaPrivateKey_rejectsPkcs8HeaderWithInvalidBase64Body() {
        String malformed = "-----BEGIN PRIVATE KEY-----\nnot-valid-base64!!!\n-----END PRIVATE KEY-----";

        assertThatThrownBy(() -> PemPrivateKeyReader.readPkcs8RsaPrivateKey(malformed))
                .isInstanceOf(IllegalStateException.class);
    }

    private static String toPkcs8Pem(PrivateKey privateKey) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(privateKey.getEncoded());
        return "-----BEGIN PRIVATE KEY-----\n" + base64 + "\n-----END PRIVATE KEY-----";
    }
}
