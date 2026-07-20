package com.gatekeeper.security.ratelimit;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The only class that knows the configured shape of each named rate limit
 * (Milestone 10: Security Hardening Section 3). Controllers call one of the
 * {@code check*} methods below and never see {@link RateLimiter},
 * {@link RateLimitRule}, or Bucket4j - keeping "which endpoints are limited,
 * and by how much" in one place instead of scattered across controllers.
 * <p>
 * Login deliberately checks two independent buckets - IP and account email -
 * rather than one bucket keyed by their combination. A combined key lets an
 * attacker distributing the same credential-stuffing attempt across many IPs
 * bypass a per-IP limit entirely (each IP+email pair is "new"), and lets one
 * IP attacking many accounts bypass a per-account limit the same way. Two
 * independent buckets close both gaps: exceeding *either* limit rejects the
 * request.
 */
@Service
public class RateLimitService {

    private final RateLimiter rateLimiter;

    private final RateLimitRule loginIpRule;
    private final RateLimitRule loginAccountRule;
    private final RateLimitRule refreshRule;
    private final RateLimitRule webhookRule;
    private final RateLimitRule repositorySyncRule;

    public RateLimitService(
            RateLimiter rateLimiter,
            @Value("${gatekeeper.rate-limit.login-ip.capacity:10}") int loginIpCapacity,
            @Value("${gatekeeper.rate-limit.login-ip.refill-period-seconds:60}") long loginIpRefillSeconds,
            @Value("${gatekeeper.rate-limit.login-account.capacity:5}") int loginAccountCapacity,
            @Value("${gatekeeper.rate-limit.login-account.refill-period-seconds:60}") long loginAccountRefillSeconds,
            @Value("${gatekeeper.rate-limit.refresh.capacity:10}") int refreshCapacity,
            @Value("${gatekeeper.rate-limit.refresh.refill-period-seconds:60}") long refreshRefillSeconds,
            @Value("${gatekeeper.rate-limit.webhook.capacity:100}") int webhookCapacity,
            @Value("${gatekeeper.rate-limit.webhook.refill-period-seconds:60}") long webhookRefillSeconds,
            @Value("${gatekeeper.rate-limit.repository-sync.capacity:5}") int repositorySyncCapacity,
            @Value("${gatekeeper.rate-limit.repository-sync.refill-period-seconds:60}") long repositorySyncRefillSeconds) {
        this.rateLimiter = rateLimiter;
        this.loginIpRule = new RateLimitRule(loginIpCapacity, loginIpCapacity, Duration.ofSeconds(loginIpRefillSeconds));
        this.loginAccountRule =
                new RateLimitRule(loginAccountCapacity, loginAccountCapacity, Duration.ofSeconds(loginAccountRefillSeconds));
        this.refreshRule = new RateLimitRule(refreshCapacity, refreshCapacity, Duration.ofSeconds(refreshRefillSeconds));
        this.webhookRule = new RateLimitRule(webhookCapacity, webhookCapacity, Duration.ofSeconds(webhookRefillSeconds));
        this.repositorySyncRule = new RateLimitRule(
                repositorySyncCapacity, repositorySyncCapacity, Duration.ofSeconds(repositorySyncRefillSeconds));
    }

    public void checkLogin(String clientIp, String email) {
        check("auth.login.ip", "ip:" + clientIp, loginIpRule);
        check("auth.login.account", "account:" + email.toLowerCase(), loginAccountRule);
    }

    public void checkRefresh(String clientIp) {
        check("auth.refresh.ip", "ip:" + clientIp, refreshRule);
    }

    /** One global bucket, not keyed per-sender - GitHub's own source IPs are a documented, changing range not worth keying on individually. */
    public void checkWebhook() {
        check("github.webhook", "global", webhookRule);
    }

    public void checkRepositorySync(Long userId) {
        check("github.repository-sync", "user:" + userId, repositorySyncRule);
    }

    private void check(String endpointTag, String key, RateLimitRule rule) {
        if (!rateLimiter.tryConsume(endpointTag + ':' + key, rule)) {
            throw new RateLimitExceededException(endpointTag);
        }
    }
}
