package com.waangu.platform.db;

import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Initializes PostgreSQL session variables for row-level security (RLS).
 * <p>
 * Sets session-local variables that are used by RLS policies to enforce tenant isolation:
 * <ul>
 *   <li>app.current_tenant - Tenant ID for RLS filtering</li>
 *   <li>app.current_country - Country code for regional data isolation</li>
 *   <li>app.current_legal_entity - Legal entity ID for financial operations</li>
 *   <li>search_path - PostgreSQL schema for SCHEMA mode tenants</li>
 * </ul>
 * </p>
 * <p>
 * Must be called at the start of each transaction to ensure proper isolation.
 * </p>
 */
@Component
@ConditionalOnBean(JdbcTemplate.class)
public class DbSessionInitializer {

    private final JdbcTemplate jdbcTemplate;

    public DbSessionInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Initializes session variables for the current transaction.
     * <p>
     * Call this at the beginning of a @Transactional method to ensure RLS policies
     * have access to the current tenant context.
     * </p>
     *
     * @throws IllegalStateException if TenantContext is not available
     */
    public void initForTx() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) {
            throw new IllegalStateException("Missing TenantContext");
        }

        // Use parameterized queries to prevent SQL injection
        jdbcTemplate.update("SELECT set_config('app.current_tenant', ?, true)", ctx.tenantId());
        jdbcTemplate.update("SELECT set_config('app.current_country', ?, true)", ctx.countryCode());
        
        if (ctx.legalEntityId() != null && !ctx.legalEntityId().isBlank()) {
            jdbcTemplate.update("SELECT set_config('app.current_legal_entity', ?, true)", ctx.legalEntityId());
        }
        
        if ("SCHEMA".equalsIgnoreCase(ctx.tenantMode()) && ctx.tenantSchema() != null) {
            // Validate schema name to prevent SQL injection (alphanumeric + underscore only)
            if (!ctx.tenantSchema().matches("^[a-zA-Z0-9_]+$")) {
                throw new IllegalArgumentException("Invalid tenant schema name: " + ctx.tenantSchema());
            }
            jdbcTemplate.execute("SET LOCAL search_path TO " + ctx.tenantSchema() + ", public");
        }
    }
}
