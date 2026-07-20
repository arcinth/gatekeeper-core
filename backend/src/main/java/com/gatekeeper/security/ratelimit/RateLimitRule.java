package com.gatekeeper.security.ratelimit;

import java.time.Duration;

/**
 * A named token-bucket shape: allow {@code capacity} requests, refilling
 * {@code refillTokens} every {@code refillPeriod} (Milestone 10: Security
 * Hardening). Deliberately a plain value type with no Bucket4j types in its
 * signature, so callers of {@link RateLimiter} never need a Bucket4j import.
 */
public record RateLimitRule(int capacity, int refillTokens, Duration refillPeriod) {
}
