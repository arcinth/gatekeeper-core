package com.gatekeeper.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Assigns every request a correlation id, so every {@link com.gatekeeper.auditlog.AuditLog}
 * row written while handling that request can be tied back to it
 * (Milestone 7: Enterprise Audit Logging, refinement 3 - "each audit record
 * should carry a correlation/request identifier"). Not yet exposed in the
 * UI, but present in the model now for future request-tracing.
 * <p>
 * Reuses an incoming {@code X-Correlation-Id} header when the caller (or an
 * upstream proxy) already set one, otherwise generates a fresh one - either
 * way, the id is echoed back on the response so a caller can always find the
 * audit trail for their own request. Stored in SLF4J's MDC rather than
 * threaded explicitly through every service call, so {@link com.gatekeeper.auditlog.AuditLogService}
 * (and, incidentally, every log line for the duration of the request) can
 * read it without every intermediate method signature needing to carry it.
 * Highest-precedence order so the id is available for the entire filter
 * chain, including authentication.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    public static final String HEADER_NAME = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
