package com.gatekeeper.github;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gatekeeper.github.exception.InvalidWebhookSignatureException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTest {

    private static final String SECRET = "test-webhook-secret";

    private final WebhookSignatureVerifier verifier = new WebhookSignatureVerifier(SECRET, new SimpleMeterRegistry());

    @Test
    void verify_acceptsCorrectlySignedPayload() {
        byte[] body = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

        assertThatCode(() -> verifier.verify(body, sign(body, SECRET))).doesNotThrowAnyException();
    }

    @Test
    void verify_rejectsSignatureComputedWithWrongSecret() {
        byte[] body = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier.verify(body, sign(body, "a-different-secret")))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void verify_rejectsWhenBodyWasTamperedAfterSigning() {
        byte[] signedBody = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);
        String signature = sign(signedBody, SECRET);
        byte[] tamperedBody = "{\"action\":\"closed\"}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier.verify(tamperedBody, signature))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void verify_rejectsMissingSignatureHeader() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier.verify(body, null))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void verify_rejectsHeaderMissingSha256Prefix() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier.verify(body, "not-a-valid-signature-header"))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void verify_rejectsNonHexSignatureValue() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> verifier.verify(body, "sha256=not-valid-hex-zzzz"))
                .isInstanceOf(InvalidWebhookSignatureException.class);
    }

    @Test
    void verify_rejectsEmptyBody() {
        // GitHub always sends a JSON body, but the verifier itself should not assume
        // a non-empty payload - an empty body still has a well-defined HMAC.
        byte[] emptyBody = new byte[0];

        assertThatCode(() -> verifier.verify(emptyBody, sign(emptyBody, SECRET))).doesNotThrowAnyException();
    }

    private static String sign(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
