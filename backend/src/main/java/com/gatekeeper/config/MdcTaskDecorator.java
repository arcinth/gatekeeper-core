package com.gatekeeper.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

/**
 * Copies the submitting thread's MDC context (correlationId, requestId,
 * userId, organizationId - see {@link CorrelationIdFilter}/
 * {@code JwtAuthenticationFilter}) onto the {@code ThreadPoolTaskExecutor}
 * worker thread that actually runs an {@code @Async} method (Milestone 9:
 * Observability).
 * <p>
 * Necessary because MDC is thread-local: without this, every log line
 * produced by {@code AnalysisExecutionService}/{@code AIReviewExecutionService}
 * (both of which run on a dedicated pool, not the request thread - see
 * {@link AsyncConfig}) would be missing the correlation id entirely, breaking
 * exactly the request-tracing this milestone exists to provide for the two
 * places in the codebase where it matters most - the async pipeline is where
 * a slow or failed run is hardest to diagnose without it.
 * <p>
 * Pool threads are reused across many requests, so the decorated task must
 * restore whatever context (if any) that worker thread had before this task
 * ran, once it finishes - never simply clear it - otherwise a later task on
 * the same pooled thread could start with a previous task's stale context.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> callerContext = MDC.getCopyOfContextMap();
        return () -> {
            Map<String, String> workerContext = MDC.getCopyOfContextMap();
            try {
                setContext(callerContext);
                runnable.run();
            } finally {
                setContext(workerContext);
            }
        };
    }

    private void setContext(Map<String, String> context) {
        if (context != null) {
            MDC.setContextMap(context);
        } else {
            MDC.clear();
        }
    }
}
