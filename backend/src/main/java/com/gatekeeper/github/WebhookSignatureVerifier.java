package com.gatekeeper.github;

import com.gatekeeper.config.SecretSanitizer;
import com.gatekeeper.github.exception.InvalidWebhookSignatureException;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class WebhookSignatureVerifier {

    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final String webhookSecret;
    private final MeterRegistry meterRegistry;

    public WebhookSignatureVerifier(
            @Value("${gatekeeper.github.webhook.secret}") String webhookSecret, MeterRegistry meterRegistry) {
        // Sanitized here, not just at startup-diagnostic time - this is the value
        // actually used to compute every signature, and must match GitHub's
        // configured secret byte-for-byte (see SecretSanitizer's Javadoc for why
        // a naive @Value binding alone isn't safe against a polluted value).
        this.webhookSecret = SecretSanitizer.sanitize(webhookSecret);
        this.meterRegistry = meterRegistry;
    }

    /**
     * Must run against the exact bytes GitHub sent, before any JSON parsing -
     * GitHub signs the raw request body, so re-serializing a parsed payload would
     * not reproduce the same signature even if the data were unchanged. The webhook
     * secret itself and the raw signature bytes are never logged (Milestone 9:
     * Observability - Security) - only the fact that verification failed.
     */
    public void verify(byte[] rawBody, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            meterRegistry.counter("gatekeeper.webhook.signature.failures").increment();
            throw new InvalidWebhookSignatureException("Missing or malformed X-Hub-Signature-256 header.");
        }

        byte[] providedSignature;
        try {
            providedSignature = HexFormat.of().parseHex(signatureHeader.substring(SIGNATURE_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            meterRegistry.counter("gatekeeper.webhook.signature.failures").increment();
            throw new InvalidWebhookSignatureException("X-Hub-Signature-256 header is not valid hex.");
        }

        byte[] expectedSignature = computeSignature(rawBody);

        // MessageDigest.isEqual is a constant-time comparison - a naive Arrays.equals
        // or byte-by-byte loop would leak how many leading bytes matched via timing.
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
            meterRegistry.counter("gatekeeper.webhook.signature.failures").increment();
            throw new InvalidWebhookSignatureException("Webhook signature does not match the configured secret.");
        }
    }

    private byte[] computeSignature(byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(rawBody);
        } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
            // HmacSHA256 is a standard JDK algorithm and webhookSecret is always a
            // non-null configured value, so this path should be unreachable.
            throw new IllegalStateException("Unable to compute webhook signature.", ex);
        }
    }
}
