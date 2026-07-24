package com.gatekeeper.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretSanitizerTest {

    @Test
    void sanitize_returnsNullForNull() {
        assertThat(SecretSanitizer.sanitize(null)).isNull();
    }

    @Test
    void sanitize_leavesAnAlreadyCleanValueUnchanged() {
        assertThat(SecretSanitizer.sanitize("clean-secret-value")).isEqualTo("clean-secret-value");
    }

    @Test
    void sanitize_stripsLeadingAndTrailingWhitespace() {
        assertThat(SecretSanitizer.sanitize("  secret-value  ")).isEqualTo("secret-value");
    }

    @Test
    void sanitize_stripsATrailingNewline() {
        assertThat(SecretSanitizer.sanitize("secret-value\n")).isEqualTo("secret-value");
    }

    @Test
    void sanitize_stripsALeadingByteOrderMark() {
        assertThat(SecretSanitizer.sanitize("﻿secret-value")).isEqualTo("secret-value");
    }

    @Test
    void sanitize_stripsBothABomAndSurroundingWhitespace() {
        assertThat(SecretSanitizer.sanitize("﻿  secret-value  \n")).isEqualTo("secret-value");
    }

    @Test
    void sanitize_returnsEmptyStringForWhitespaceOnlyInput() {
        assertThat(SecretSanitizer.sanitize("   \n  ")).isEmpty();
    }
}
