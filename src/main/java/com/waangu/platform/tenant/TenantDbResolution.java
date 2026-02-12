package com.waangu.platform.tenant;

/**
 * DB resolution for a tenant (from Tenant Registry) — chagpt.
 */
public record TenantDbResolution(
    String mode,           // POOLED | SCHEMA | DEDICATED_DB
    String jdbcUrl,
    String username,
    String passwordRef,
    String schema
) {}
