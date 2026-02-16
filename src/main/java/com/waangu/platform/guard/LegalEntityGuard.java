package com.waangu.platform.guard;

import com.waangu.platform.annotation.FinancialEndpoint;
import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * AOP aspect that enforces legal entity requirement for financial endpoints.
 * <p>
 * Intercepts methods or classes annotated with {@link FinancialEndpoint} and validates
 * that a legal entity ID is present in the current tenant context. This ensures proper
 * entity-level isolation for financial operations and compliance with accounting standards.
 * </p>
 * <p>
 * If the legal entity ID is missing or blank, the request is rejected with a 400 Bad Request.
 * </p>
 *
 * @see FinancialEndpoint
 */
@Aspect
@Component
public class LegalEntityGuard {

    /**
     * Enforces legal entity requirement for financial endpoints.
     *
     * @param joinPoint the intercepted method invocation
     * @return the result of the intercepted method
     * @throws Throwable if the method throws an exception
     * @throws ResponseStatusException if legal entity ID is missing
     */
    @Around("@within(com.waangu.platform.annotation.FinancialEndpoint) || @annotation(com.waangu.platform.annotation.FinancialEndpoint)")
    public Object requireLegalEntity(ProceedingJoinPoint joinPoint) throws Throwable {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null || ctx.legalEntityId() == null || ctx.legalEntityId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LEGAL_ENTITY_REQUIRED");
        }
        return joinPoint.proceed();
    }
}
