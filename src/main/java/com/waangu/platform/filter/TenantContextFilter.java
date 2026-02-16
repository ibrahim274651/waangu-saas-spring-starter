package com.waangu.platform.filter;

import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Filter that extracts and validates tenant context from JWT tokens.
 * <p>
 * Enforces the SaaS contract by validating:
 * <ul>
 *   <li>Tenant ID and mode (POOLED/SCHEMA/DEDICATED_DB)</li>
 *   <li>Billing status (ACTIVE or TRIAL)</li>
 *   <li>Module enablement (if moduleId is configured)</li>
 *   <li>Country code and locale</li>
 *   <li>Legal entity ID (required for write operations)</li>
 * </ul>
 * </p>
 * <p>
 * The extracted context is stored in {@link com.waangu.platform.tenant.TenantContextHolder}
 * for the duration of the request and automatically cleared afterward.
 * </p>
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private final String moduleId;

    public TenantContextFilter() {
        this.moduleId = null;
    }

    public TenantContextFilter(String moduleId) {
        this.moduleId = moduleId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing JWT");
            return;
        }
        Jwt jwt = jwtAuth.getToken();

        String tenantId = getClaimAsString(jwt, "tenant_id");
        String tenantMode = getClaimAsString(jwt, "tenant_mode");
        String billingStatus = getClaimAsString(jwt, "billing_status");
        List<String> enabledModules = getClaimAsStringList(jwt, "enabled_modules");
        String legalEntityId = getClaimAsString(jwt, "legal_entity_id");
        String countryCode = getClaimAsString(jwt, "country_code");
        String locale = getClaimAsString(jwt, "locale");
        String subscriptionId = getClaimAsString(jwt, "subscription_id");
        String userId = getClaimAsString(jwt, "user_id");

        String correlationId = Optional.ofNullable(request.getHeader("X-Correlation-Id"))
                .filter(s -> !s.isBlank())
                .orElse(UUID.randomUUID().toString());

        if (tenantId == null || tenantMode == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "MISSING_TENANT_CONTEXT");
            return;
        }
        try {
            UUID.fromString(tenantId);
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid tenant_id");
            return;
        }

        if (billingStatus == null || (!"ACTIVE".equalsIgnoreCase(billingStatus) && !"TRIAL".equalsIgnoreCase(billingStatus))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "TENANT_SUSPENDED");
            return;
        }
        if (moduleId != null && !moduleId.isBlank() && (enabledModules == null || !enabledModules.contains(moduleId))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "MODULE_DISABLED");
            return;
        }

        if (countryCode == null || !countryCode.matches("^[A-Za-z]{2}$")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "INVALID_COUNTRY_CODE");
            return;
        }
        if (locale == null || locale.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "LOCALE_REQUIRED");
            return;
        }
        if (subscriptionId == null || subscriptionId.isBlank()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "subscription_id required");
            return;
        }

        boolean isWrite = !"GET".equalsIgnoreCase(request.getMethod());
        if (isWrite && (legalEntityId == null || legalEntityId.isBlank())) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "LEGAL_ENTITY_REQUIRED");
            return;
        }

        String schema = "SCHEMA".equalsIgnoreCase(tenantMode) ? "tenant_" + tenantId.replace("-", "") : null;

        TenantContext ctx = new TenantContext(
                tenantId, tenantMode, legalEntityId, countryCode, locale,
                subscriptionId, correlationId, userId != null ? userId : "", schema
        );
        TenantContextHolder.set(ctx);
        MDC.put("tenant_id", tenantId);
        MDC.put("correlation_id", correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            TenantContextHolder.clear();
            MDC.remove("tenant_id");
            MDC.remove("correlation_id");
        }
    }

    private static String getClaimAsString(Jwt jwt, String claim) {
        Object v = jwt.getClaim(claim);
        return v != null ? v.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static List<String> getClaimAsStringList(Jwt jwt, String claim) {
        Object v = jwt.getClaim(claim);
        if (v instanceof List) return (List<String>) v;
        return null;
    }
}
