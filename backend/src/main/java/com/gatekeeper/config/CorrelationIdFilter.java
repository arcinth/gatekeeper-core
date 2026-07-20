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
 * Assigns every request a correlation id and a request id, and owns the MDC
 * lifecycle for the whole request. Highest-precedence order so both ids are
 * available for the entire filter chain, including authentication.
 * <p>
 * <b>correlationId</b> (Milestone 7: Enterprise Audit Logging, refinement 3 -
 * "each audit record should carry a correlation/request identifier") ties
 * every {@link com.gatekeeper.auditlog.AuditLog} row written while handling a
 * request back to it. Reuses an incoming {@code X-Correlation-Id} header when
 * the caller (or an upstream proxy) already set one, otherwise generates a
 * fresh one - either way, the id is echoed back on the response. Because a
 * caller can supply this value, it must never be treated as a trust boundary
 * (an authorization/idempotency key) - it is a tracing aid only.
 * <p>
 * <b>requestId</b> (Milestone 9: Observability) is deliberately always
 * server-generated, never taken from a header - it identifies exactly this
 * one HTTP call, distinct from correlationId, which a client can deliberately
 * share across several logical calls that make up one operation.
 * <p>
 * Both are stored in SLF4J's MDC rather than threaded explicitly through
 * every service call, so {@link com.gatekeeper.auditlog.AuditLogService},
 * {@link com.gatekeeper.security.JwtAuthenticationFilter} (which adds
 * userId/organizationId to the same MDC map once authentication resolves),
 * and every log line for the duration of the request can read them without
 * every intermediate method signature needing to carry them.
 * <p>
 * This filter is the single owner of the MDC lifecycle for a request: its
 * {@code finally} block calls {@link MDC#clear()} - not just removing its own
 * two keys - so that anything added later in the chain (userId,
 * organizationId, or ad hoc keys a specific handler adds) is guaranteed
 * cleared before the underlying Tomcat worker thread is returned to the pool
 * and reused for an unrelated request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String MDC_KEY = "correlationId";
    public static final String REQUEST_ID_MDC_KEY = "requestId";
    public static final String HEADER_NAME = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        MDC.put(REQUEST_ID_MDC_KEY, UUID.randomUUID().toString());
        response.setHeader(HEADER_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
