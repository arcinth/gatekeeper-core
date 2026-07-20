package com.gatekeeper.security.ratelimit;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Single-instance, in-memory {@link RateLimiter} (Milestone 10: Security
 * Hardening Section 3) - one {@link Bucket} per key, keyed by whatever the
 * caller passes (e.g. {@code "auth.login.ip:203.0.113.4"}). Not shared across
 * multiple application instances; see docs/Security-Hardening.md for the
 * documented (not yet built) Redis-backed upgrade path for horizontal
 * scaling, which would implement this same {@link RateLimiter} interface.
 * <p>
 * A bucket is created the first time its key is seen and never removed while
 * it is still partially consumed - {@link #evictIdleBuckets()} periodically
 * drops entries that have fully refilled back to capacity, since a bucket at
 * full capacity behaves identically whether it is kept or re-created on next
 * use. Without this, a flood of distinct keys (e.g. an attacker cycling
 * through random login emails) would grow this map without bound, turning
 * the rate limiter itself into a memory-exhaustion vector.
 */
@Slf4j
@Component
public class InMemoryRateLimiter implements RateLimiter {

    private record BucketEntry(Bucket bucket, long capacity) {
    }

    private final Map<String, BucketEntry> buckets = new ConcurrentHashMap<>();

    @Override
    public boolean tryConsume(String key, RateLimitRule rule) {
        BucketEntry entry = buckets.computeIfAbsent(key, k -> new BucketEntry(newBucket(rule), rule.capacity()));
        return entry.bucket().tryConsume(1);
    }

    private Bucket newBucket(RateLimitRule rule) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(rule.capacity())
                .refillGreedy(rule.refillTokens(), rule.refillPeriod())
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    @Scheduled(fixedDelayString = "${gatekeeper.rate-limit.cleanup-interval-ms:300000}")
    void evictIdleBuckets() {
        int sizeBefore = buckets.size();
        buckets.values().removeIf(entry -> entry.bucket().getAvailableTokens() >= entry.capacity());
        int evicted = sizeBefore - buckets.size();
        if (evicted > 0) {
            log.debug("Evicted {} fully-refilled rate-limit bucket(s); {} remaining.", evicted, buckets.size());
        }
    }
}
