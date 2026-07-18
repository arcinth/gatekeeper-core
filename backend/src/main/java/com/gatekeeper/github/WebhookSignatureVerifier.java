package com.gatekeeper.github;

import com.gatekeeper.github.exception.InvalidWebhookSignatureException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WebhookSignatureVerifier {

    private static final String SIGNATURE_PREFIX = "sha256=";
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String RAW_ENV_VAR_NAME = "GITHUB_WEBHOOK_SECRET";

    private final String webhookSecret;

    public WebhookSignatureVerifier(
            @Value("${gatekeeper.github.webhook.secret}") String webhookSecret, Environment environment) {
        this.webhookSecret = webhookSecret;
        // TEMPORARY DIAGNOSTIC - remove once the secret-mismatch issue is resolved.
        // Logs length, masked preview, and which PropertySource actually supplied
        // the raw GITHUB_WEBHOOK_SECRET value - never the secret itself.
        log.info("WebhookSignatureVerifier configured with a {}-character secret ({}).",
                webhookSecret.length(), mask(webhookSecret));
        log.info("Raw '{}' env var found in property source: {}",
                RAW_ENV_VAR_NAME, describeSourceOf(environment, RAW_ENV_VAR_NAME));
    }

    private static String mask(String value) {
        if (value.length() <= 6) {
            return "*".repeat(value.length());
        }
        return value.substring(0, 3) + "*".repeat(value.length() - 6) + value.substring(value.length() - 3);
    }

    /**
     * Walks every registered PropertySource in precedence order and reports the
     * first one that actually contains the raw env var key - not the resolved
     * gatekeeper.github.webhook.secret property, which every source's own
     * lookup would report identically regardless of where the real value came
     * from. This is what actually answers "is something other than my shell's
     * exported variable providing this value."
     */
    private static String describeSourceOf(Environment environment, String rawKey) {
        if (!(environment instanceof ConfigurableEnvironment configurableEnvironment)) {
            return "unknown (Environment is not a ConfigurableEnvironment)";
        }
        for (PropertySource<?> source : configurableEnvironment.getPropertySources()) {
            if (source.containsProperty(rawKey)) {
                return source.getName();
            }
        }
        return "none - not set in any property source; the application.yml default is being used";
    }

    /**
     * Must run against the exact bytes GitHub sent, before any JSON parsing -
     * GitHub signs the raw request body, so re-serializing a parsed payload would
     * not reproduce the same signature even if the data were unchanged.
     */
    public void verify(byte[] rawBody, String signatureHeader) {
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            throw new InvalidWebhookSignatureException("Missing or malformed X-Hub-Signature-256 header.");
        }

        byte[] providedSignature;
        try {
            providedSignature = HexFormat.of().parseHex(signatureHeader.substring(SIGNATURE_PREFIX.length()));
        } catch (IllegalArgumentException ex) {
            throw new InvalidWebhookSignatureException("X-Hub-Signature-256 header is not valid hex.");
        }

        byte[] expectedSignature = computeSignature(rawBody);

        // MessageDigest.isEqual is a constant-time comparison - a naive Arrays.equals
        // or byte-by-byte loop would leak how many leading bytes matched via timing.
        if (!MessageDigest.isEqual(expectedSignature, providedSignature)) {
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
