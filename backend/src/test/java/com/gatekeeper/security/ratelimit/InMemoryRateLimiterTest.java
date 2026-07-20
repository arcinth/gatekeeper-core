package com.gatekeeper.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class InMemoryRateLimiterTest {

    private final InMemoryRateLimiter rateLimiter = new InMemoryRateLimiter();

    @Test
    void tryConsume_allowsUpToCapacityRequestsThenRejects() {
        RateLimitRule rule = new RateLimitRule(3, 3, Duration.ofMinutes(1));

        assertThat(rateLimiter.tryConsume("key", rule)).isTrue();
        assertThat(rateLimiter.tryConsume("key", rule)).isTrue();
        assertThat(rateLimiter.tryConsume("key", rule)).isTrue();
        assertThat(rateLimiter.tryConsume("key", rule)).isFalse();
    }

    @Test
    void tryConsume_tracksDistinctKeysIndependently() {
        RateLimitRule rule = new RateLimitRule(1, 1, Duration.ofMinutes(1));

        assertThat(rateLimiter.tryConsume("a", rule)).isTrue();
        assertThat(rateLimiter.tryConsume("a", rule)).isFalse();
        assertThat(rateLimiter.tryConsume("b", rule)).isTrue();
    }

    @Test
    void evictIdleBuckets_neverResetsABucketThatIsStillPartiallyOrFullyConsumed() {
        RateLimitRule rule = new RateLimitRule(2, 2, Duration.ofMinutes(1));
        rateLimiter.tryConsume("exhausted", rule);
        rateLimiter.tryConsume("exhausted", rule); // now at 0/2 available - not idle

        rateLimiter.evictIdleBuckets();

        // If the sweep had wrongly evicted an exhausted bucket, a fresh one would be
        // created here and this would incorrectly succeed - it must still be rejected.
        assertThat(rateLimiter.tryConsume("exhausted", rule)).isFalse();
    }
}
