package com.gatekeeper.config;

/**
 * Strips the two forms of accidental pollution a secret pasted from a text
 * editor, browser, or .env file commonly picks up: a leading UTF-8 byte-order
 * mark (U+FEFF, invisible in most editors and terminals) and leading/trailing
 * whitespace (including a trailing newline some tools append even for a
 * "single-line" value). Both silently change the byte sequence an HMAC is
 * computed over without changing how the secret looks on screen - exactly
 * what makes a webhook signature mismatch so hard to spot by eye. Shared by
 * WebhookSignatureVerifier (the actual point of use) and
 * GitHubAppConfigurationDiagnostics (which reports whether a raw value needed
 * sanitizing at all), so both agree on exactly what "the secret" means.
 */
public final class SecretSanitizer {

    private static final char BOM = '﻿';

    private SecretSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null) {
            return null;
        }
        String withoutBom = value.length() > 0 && value.charAt(0) == BOM ? value.substring(1) : value;
        return withoutBom.trim();
    }
}
