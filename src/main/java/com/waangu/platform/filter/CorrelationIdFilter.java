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
 * Filter that ensures every request has a correlation ID for distributed tracing.
 * <p>
 * If the incoming request contains an X-Correlation-Id header, it is used; otherwise,
 * a new UUID is generated. The correlation ID is added to the SLF4J MDC for logging
 * and included in the response headers.
 * </p>
 */
public class CorrelationIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String cid = Optional.ofNullable(request.getHeader("X-Correlation-Id"))
                .filter(s -> !s.isBlank())
                .orElse(UUID.randomUUID().toString());
        MDC.put("correlation_id", cid);
        response.setHeader("X-Correlation-Id", cid);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlation_id");
        }
    }
}
