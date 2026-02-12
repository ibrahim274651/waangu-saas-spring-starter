package com.waangu.platform.guard;

// import com.waangu.platform.annotation.FinancialEndpoint;
import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Aspect
@Component
public class LegalEntityGuard {

    @Around("@within(FinancialEndpoint) || @annotation(FinancialEndpoint)")
    public Object requireLegalEntity(ProceedingJoinPoint joinPoint) throws Throwable {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null || ctx.legalEntityId() == null || ctx.legalEntityId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "LEGAL_ENTITY_REQUIRED");
        }
        return joinPoint.proceed();
    }
}
