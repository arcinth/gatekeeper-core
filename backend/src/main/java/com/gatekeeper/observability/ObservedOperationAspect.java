package com.gatekeeper.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Times every {@link ObservedOperation}-annotated method call, records it as
 * a {@code gatekeeper.operation.duration} Micrometer Timer, and logs a WARN
 * when it exceeds that operation category's configured threshold (Milestone
 * 9: Observability, Section 7 - Performance Monitoring).
 * <p>
 * Reuses {@code spring-boot-starter-aop} (already a dependency, previously
 * unused) rather than adding a new library. Tags ({@code operation},
 * {@code category}, {@code outcome}) are all fixed, low-cardinality enums or
 * annotation-supplied constants - never a repository/PR/user/organization id
 * - per this milestone's explicit cardinality requirement.
 * <p>
 * When the annotated method is also {@code @Retryable} (e.g.
 * {@code GitHubApiClient.fetchPullRequestFiles}), this aspect's timer
 * measures per-attempt duration, not the cumulative retried operation - each
 * attempt genuinely is a separate GitHub API call, so this is the more
 * accurate, not a degraded, signal.
 */
@Slf4j
@Aspect
@Component
public class ObservedOperationAspect {

    private static final String METRIC_NAME = "gatekeeper.operation.duration";

    private final MeterRegistry meterRegistry;
    private final long githubApiThresholdMs;
    private final long policyEvaluationThresholdMs;
    private final long securityEvaluationThresholdMs;
    private final long reviewEvaluationThresholdMs;
    private final long analysisPipelineThresholdMs;

    public ObservedOperationAspect(
            MeterRegistry meterRegistry,
            @Value("${gatekeeper.observability.thresholds.github-api-ms}") long githubApiThresholdMs,
            @Value("${gatekeeper.observability.thresholds.policy-evaluation-ms}") long policyEvaluationThresholdMs,
            @Value("${gatekeeper.observability.thresholds.security-evaluation-ms}") long securityEvaluationThresholdMs,
            @Value("${gatekeeper.observability.thresholds.review-evaluation-ms}") long reviewEvaluationThresholdMs,
            @Value("${gatekeeper.observability.thresholds.analysis-pipeline-ms}") long analysisPipelineThresholdMs) {
        this.meterRegistry = meterRegistry;
        this.githubApiThresholdMs = githubApiThresholdMs;
        this.policyEvaluationThresholdMs = policyEvaluationThresholdMs;
        this.securityEvaluationThresholdMs = securityEvaluationThresholdMs;
        this.reviewEvaluationThresholdMs = reviewEvaluationThresholdMs;
        this.analysisPipelineThresholdMs = analysisPipelineThresholdMs;
    }

    @Around("@annotation(observedOperation)")
    public Object around(ProceedingJoinPoint joinPoint, ObservedOperation observedOperation) throws Throwable {
        long startNanos = System.nanoTime();
        String outcome = "success";
        try {
            return joinPoint.proceed();
        } catch (Throwable ex) {
            outcome = "error";
            throw ex;
        } finally {
            long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
            recordTimer(observedOperation, outcome, elapsedMs);
            warnIfSlow(observedOperation, elapsedMs);
        }
    }

    private void recordTimer(ObservedOperation observedOperation, String outcome, long elapsedMs) {
        Timer.builder(METRIC_NAME)
                .description("Duration of an @ObservedOperation-annotated method call")
                .tag("operation", observedOperation.value())
                .tag("category", observedOperation.category().name())
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(elapsedMs, TimeUnit.MILLISECONDS);
    }

    private void warnIfSlow(ObservedOperation observedOperation, long elapsedMs) {
        long threshold = thresholdFor(observedOperation.category());
        if (elapsedMs > threshold) {
            log.warn("Slow operation: '{}' ({}) took {}ms, exceeding the {}ms threshold.",
                    observedOperation.value(), observedOperation.category(), elapsedMs, threshold);
        }
    }

    private long thresholdFor(OperationCategory category) {
        return switch (category) {
            case GITHUB_API -> githubApiThresholdMs;
            case POLICY_ENGINE -> policyEvaluationThresholdMs;
            case SECURITY_ENGINE -> securityEvaluationThresholdMs;
            case REVIEW_ENGINE -> reviewEvaluationThresholdMs;
            case ANALYSIS_PIPELINE -> analysisPipelineThresholdMs;
        };
    }
}
