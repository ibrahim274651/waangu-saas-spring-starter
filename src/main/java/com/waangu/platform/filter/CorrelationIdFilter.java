package com.waangu.platform.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter that ensures every request has a correlation/trace ID for distributed tracing.
 * <p>
 * Supports both X-Correlation-Id and X-Trace-Id headers for compatibility.
 * If either header is present, it is used; otherwise, a new UUID is generated.
 * The ID is added to the SLF4J MDC for logging (as both "correlation_id" and "trace_id")
 * and included in the response headers.
 * </p>
 * <p>
 * This enables integration with:
 * <ul>
 *   <li>Chat support systems (via trace_id correlation)</li>
 *   <li>Audit logs (via correlation_id)</li>
 *   <li>Distributed tracing systems (OpenTelemetry, Jaeger, etc.)</li>
 * </ul>
 * </p>
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        
        String traceId = Optional.ofNullable(request.getHeader("X-Trace-Id"))
                .filter(s -> !s.isBlank())
                .orElse(null);
        
        String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id"))
                .filter(s -> !s.isBlank())
                .orElse(traceId);
        
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
        }
        
        MDC.put("correlation_id", correlationId);
        MDC.put("trace_id", correlationId);
        
        response.setHeader("X-Correlation-Id", correlationId);
        response.setHeader("X-Trace-Id", correlationId);
        
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlation_id");
            MDC.remove("trace_id");
        }
    }
}
