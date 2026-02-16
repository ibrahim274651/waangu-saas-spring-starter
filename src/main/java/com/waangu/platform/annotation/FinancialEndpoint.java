package com.waangu.platform.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for financial endpoints that require legal_entity_id in tenant context.
 * <p>
 * When applied to a controller method or class, the {@link com.waangu.platform.guard.LegalEntityGuard}
 * aspect will enforce that a valid legal entity ID is present before allowing the request to proceed.
 * This is critical for financial operations to ensure proper entity-level isolation and compliance.
 * </p>
 *
 * @see com.waangu.platform.guard.LegalEntityGuard
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface FinancialEndpoint {}
