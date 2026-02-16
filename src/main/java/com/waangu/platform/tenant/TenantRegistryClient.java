package com.waangu.platform.tenant;

/**
 * Client interface for resolving tenant database routing information.
 * <p>
 * Queries the tenant registry service to obtain database connection details
 * based on the tenant's isolation mode (POOLED, SCHEMA, or DEDICATED_DB).
 * </p>
 *
 * @see com.waangu.platform.tenant.HttpTenantRegistryClient
 */
public interface TenantRegistryClient {
    TenantDbResolution resolve(String tenantId);
}
