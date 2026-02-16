package com.waangu.platform.tenant;

/**
 * Record representing database resolution information for a tenant.
 * <p>
 * Returned by {@link TenantRegistryClient} to provide connection details
 * based on the tenant's isolation mode.
 * </p>
 */
public record TenantDbResolution(
    String mode,           // POOLED | SCHEMA | DEDICATED_DB
    String jdbcUrl,
    String username,
    String passwordRef,
    String schema
) {}
