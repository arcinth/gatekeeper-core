package com.gatekeeper.security.ratelimit;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Uses the real {@link InMemoryRateLimiter} rather than a mock - the behavior
 * under test (independent IP/account buckets) is exactly the interaction
 * between {@link RateLimitService} and a real limiter, not something a mock
 * would exercise meaningfully.
 */
class RateLimitServiceTest {

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(
                new InMemoryRateLimiter(),
                2, 60, // login-ip: capacity 2
                2, 60, // login-account: capacity 2
                2, 60, // refresh: capacity 2
                2, 60, // webhook: capacity 2
                2, 60); // repository-sync: capacity 2
    }

    @Test
    void checkLogin_exceedingTheIpBucket_rejectsEvenWithDifferentAccounts() {
        rateLimitService.checkLogin("203.0.113.1", "a@example.com");
        rateLimitService.checkLogin("203.0.113.1", "b@example.com");

        assertThatThrownBy(() -> rateLimitService.checkLogin("203.0.113.1", "c@example.com"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkLogin_exceedingTheAccountBucket_rejectsEvenFromDifferentIps() {
        rateLimitService.checkLogin("203.0.113.1", "victim@example.com");
        rateLimitService.checkLogin("203.0.113.2", "victim@example.com");

        assertThatThrownBy(() -> rateLimitService.checkLogin("203.0.113.3", "victim@example.com"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkLogin_accountKeyIsCaseInsensitive() {
        rateLimitService.checkLogin("203.0.113.1", "Victim@Example.com");
        rateLimitService.checkLogin("203.0.113.2", "victim@example.com");

        assertThatThrownBy(() -> rateLimitService.checkLogin("203.0.113.3", "VICTIM@EXAMPLE.COM"))
                .isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkRefresh_isIndependentOfLoginBuckets() {
        rateLimitService.checkLogin("203.0.113.1", "a@example.com");
        rateLimitService.checkLogin("203.0.113.1", "b@example.com");

        assertThatCode(() -> rateLimitService.checkRefresh("203.0.113.1")).doesNotThrowAnyException();
    }

    @Test
    void checkWebhook_isASingleGlobalBucket() {
        rateLimitService.checkWebhook();
        rateLimitService.checkWebhook();

        assertThatThrownBy(rateLimitService::checkWebhook).isInstanceOf(RateLimitExceededException.class);
    }

    @Test
    void checkRepositorySync_isKeyedPerUser() {
        rateLimitService.checkRepositorySync(1L);
        rateLimitService.checkRepositorySync(1L);

        assertThatThrownBy(() -> rateLimitService.checkRepositorySync(1L)).isInstanceOf(RateLimitExceededException.class);
        assertThatCode(() -> rateLimitService.checkRepositorySync(2L)).doesNotThrowAnyException();
    }
}
