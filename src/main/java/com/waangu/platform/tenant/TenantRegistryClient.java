package com.waangu.platform.tenant;

/**
 * Resolves tenant → DB routing (chagpt). Implemented by HttpTenantRegistryClient.
 */
public interface TenantRegistryClient {
    TenantDbResolution resolve(String tenantId);
}
