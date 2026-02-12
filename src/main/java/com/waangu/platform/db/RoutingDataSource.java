package com.waangu.platform.db;

import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Routes to the appropriate DataSource based on current tenant mode (chagpt).
 * Use with a DataSource config that sets default + targetDataSources for POOLED/SCHEMA;
 * DEDICATED_DB can be resolved via a custom DataSource provider.
 */
public class RoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) return "default";
        return ctx.tenantMode() + ":" + ctx.tenantId();
    }
}
