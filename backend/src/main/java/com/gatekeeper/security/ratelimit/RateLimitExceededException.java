package com.gatekeeper.security.ratelimit;

import com.gatekeeper.exception.ApiException;
import com.gatekeeper.exception.ErrorCode;
import lombok.Getter;

/**
 * Thrown when a caller exceeds a configured rate limit (Milestone 10: Security
 * Hardening). {@code endpoint} is a fixed, small identifier (e.g. {@code
 * "auth.login.ip"}) - never a raw path or client-supplied value - so it stays
 * safe to use as a Micrometer tag in {@link com.gatekeeper.exception.GlobalExceptionHandler}.
 */
@Getter
public class RateLimitExceededException extends ApiException {

    private final String endpoint;

    public RateLimitExceededException(String endpoint) {
        super(ErrorCode.GK_429, "Too many requests. Please try again later.");
        this.endpoint = endpoint;
    }
}
