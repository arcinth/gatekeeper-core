package com.gatekeeper.security.ratelimit;

/**
 * The only interface application code depends on for rate limiting
 * (Milestone 10: Security Hardening Section 3) - {@link RateLimitService} and
 * everything upstream of it (controllers) know nothing about Bucket4j or any
 * other limiting library. {@link InMemoryRateLimiter} is the only
 * implementation today; a future Redis-backed implementation (for horizontal
 * scaling across multiple instances - see docs/Security-Hardening.md) is a
 * new class implementing this same interface, not a change to any caller.
 */
public interface RateLimiter {

    /**
     * Attempts to consume one token from the bucket identified by
     * {@code key}, creating it on first use according to {@code rule}.
     *
     * @return {@code true} if a token was available and consumed, {@code false} if the caller should be rejected.
     */
    boolean tryConsume(String key, RateLimitRule rule);
}
