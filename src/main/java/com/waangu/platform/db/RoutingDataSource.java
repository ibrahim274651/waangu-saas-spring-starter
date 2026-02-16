package com.waangu.platform.db;

import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Routes database connections to the appropriate DataSource based on tenant mode.
 * <p>
 * Supports three tenant isolation strategies:
 * <ul>
 *   <li><b>POOLED</b>: Shared database with row-level security (RLS)</li>
 *   <li><b>SCHEMA</b>: Dedicated PostgreSQL schema per tenant</li>
 *   <li><b>DEDICATED_DB</b>: Separate database per tenant</li>
 * </ul>
 * </p>
 * <p>
 * Configure with a default DataSource and targetDataSources map. For DEDICATED_DB mode,
 * implement a custom DataSource provider to dynamically resolve tenant-specific connections.
 * </p>
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) return "default";
        return ctx.tenantMode() + ":" + ctx.tenantId();
    }
}
