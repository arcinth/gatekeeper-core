package com.gatekeeper.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Backs AnalysisExecutionService's async execution (Milestone 4 Architecture,
 * ADR-013) and GitHubApiClient's @Retryable calls. A dedicated,
 * bounded-queue executor is configured explicitly rather than relying on
 * Spring's default SimpleAsyncTaskExecutor, which creates an unbounded
 * thread-per-task with no backpressure - exactly wrong for a pipeline that
 * calls a rate-limited external API.
 */
@Slf4j
@Configuration
@EnableAsync
@EnableRetry
public class AsyncConfig implements AsyncConfigurer {

    private final int corePoolSize;
    private final int maxPoolSize;
    private final int queueCapacity;

    public AsyncConfig(
            @Value("${gatekeeper.analysis.execution.core-pool-size}") int corePoolSize,
            @Value("${gatekeeper.analysis.execution.max-pool-size}") int maxPoolSize,
            @Value("${gatekeeper.analysis.execution.queue-capacity}") int queueCapacity) {
        this.corePoolSize = corePoolSize;
        this.maxPoolSize = maxPoolSize;
        this.queueCapacity = queueCapacity;
    }

    @Override
    @Bean(name = "analysisExecutionTaskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("analysis-exec-");
        executor.initialize();
        return executor;
    }

    /**
     * A safety net, not the primary failure-handling path: AnalysisExecutionService
     * catches every exception itself and marks the AnalysisRun FAILED, so reaching
     * this handler means that catch-all itself had a bug. Still worth logging
     * loudly rather than letting Spring's default handler's log line get lost.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return this::handleUncaughtAsyncException;
    }

    private void handleUncaughtAsyncException(Throwable ex, Method method, Object... params) {
        log.error("Uncaught exception in async method '{}': {}", method.getName(), ex.getMessage(), ex);
    }
}
