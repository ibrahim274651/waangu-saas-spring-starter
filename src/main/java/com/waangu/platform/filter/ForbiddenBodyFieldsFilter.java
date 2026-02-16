package com.waangu.platform.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * Security filter that rejects requests containing forbidden fields in the JSON body.
 * <p>
 * Prevents clients from injecting tenant_id, legal_entity_id, or country_code in request
 * payloads. These values must come exclusively from the validated JWT token to ensure
 * proper tenant isolation and prevent privilege escalation attacks.
 * </p>
 * <p>
 * Forbidden fields (both snake_case and camelCase):
 * <ul>
 *   <li>tenant_id / tenantId</li>
 *   <li>legal_entity_id / legalEntityId</li>
 *   <li>country_code / countryCode</li>
 * </ul>
 * </p>
 */
public class ForbiddenBodyFieldsFilter extends OncePerRequestFilter {

    private static final Set<String> FORBIDDEN = Set.of(
            "tenant_id", "tenantId",
            "legal_entity_id", "legalEntityId",
            "country_code", "countryCode");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().contains("application/json")) {
            filterChain.doFilter(request, response);
            return;
        }

        byte[] body = request.getInputStream().readAllBytes();
        if (body.length > 0) {
            String bodyStr = new String(body, StandardCharsets.UTF_8);
            for (String field : FORBIDDEN) {
                if (bodyStr.contains("\"" + field + "\"")) {
                    response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                    response.setContentType("application/json");
                    String correlationId = MDC.get("correlation_id");
                    response.getWriter().write(String.format(
                            "{\"code\":\"FORBIDDEN_FIELD\",\"messageKey\":\"error.forbidden_field\",\"args\":{\"field\":\"%s\"},\"correlationId\":\"%s\"}",
                            field, correlationId != null ? correlationId : "n/a"));
                    response.flushBuffer();
                    return;
                }
            }
        }

        HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
            @Override
            public ServletInputStream getInputStream() {
                return new ServletInputStream() {
                    private final InputStream in = new ByteArrayInputStream(body);

                    @Override
                    public boolean isFinished() {
                        return false;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener l) {
                    }

                    @Override
                    public int read() throws IOException {
                        return in.read();
                    }
                };
            }
        };
        filterChain.doFilter(wrapped, response);
    }
}
