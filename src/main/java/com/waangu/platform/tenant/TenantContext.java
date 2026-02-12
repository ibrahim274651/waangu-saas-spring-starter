package com.waangu.platform.tenant;

/**
 * Tenant context for the current request (chagpt — Waangu 360 multi-tenant hybrid).
 * Populated by TenantContextFilter from JWT claims.
 */
public record TenantContext(
    String tenantId,
    String tenantMode,       // POOLED | SCHEMA | DEDICATED_DB
    String legalEntityId,
    String countryCode,
    String locale,
    String subscriptionId,
    String correlationId,
    String userId,
    String tenantSchema     // for SCHEMA mode: e.g. tenant_&lt;uuidNoDash&gt;
) {}
