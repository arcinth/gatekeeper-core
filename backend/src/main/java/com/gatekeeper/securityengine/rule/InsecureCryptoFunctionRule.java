package com.gatekeeper.securityengine.rule;

import com.gatekeeper.securityengine.SecurityCategory;
import com.gatekeeper.securityengine.SecurityFinding;
import com.gatekeeper.securityengine.SecuritySeverity;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Flags known-weak cryptographic primitives passed as a quoted algorithm
 * name - e.g. MessageDigest.getInstance("MD5"), Cipher.getInstance("DES"),
 * crypto.createHash('sha1'). Requiring the algorithm name to appear quoted
 * (as it does in real getInstance/createHash-style calls) rather than as a
 * bare word keeps this from flagging every comment or identifier that merely
 * mentions one of these algorithms by name.
 */
@Component
public final class InsecureCryptoFunctionRule extends PatternMatchRule {

    private static final String RULE_ID = "INSECURE_CRYPTO_FUNCTION";
    private static final Pattern WEAK_ALGORITHM_PATTERN = Pattern.compile(
            "(?i)[\"'][^\"']*\\b(MD5|DES|RC4|SHA-?1|ECB)\\b[^\"']*[\"']");

    public InsecureCryptoFunctionRule() {
        super(WEAK_ALGORITHM_PATTERN);
    }

    @Override
    public String id() {
        return RULE_ID;
    }

    @Override
    public String description() {
        return "Flags known-weak cryptographic algorithms (MD5, DES, RC4, SHA-1, ECB mode) referenced by name.";
    }

    @Override
    protected SecurityFinding buildFinding(String filePath, int lineNumber, String lineContent) {
        return new SecurityFinding(
                RULE_ID,
                SecurityCategory.INSECURE_CRYPTOGRAPHY,
                SecuritySeverity.HIGH,
                filePath,
                lineNumber,
                "Insecure cryptographic algorithm referenced: " + lineContent,
                "Replace this with a modern, non-deprecated algorithm (e.g. SHA-256 or better, AES-GCM).");
    }
}
