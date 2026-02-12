package com.waangu.platform.db;

import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Sets PostgreSQL session variables for RLS (app.current_tenant, app.current_legal_entity, etc.).
 * Must be called at the start of each transaction. chagpt.
 */
@Component
public class DbSessionInitializer {

    private final JdbcTemplate jdbcTemplate;

    public DbSessionInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Call this at the beginning of a transaction (e.g. first line in a @Transactional method).
     */
    public void initForTx() {
        TenantContext ctx = TenantContextHolder.get();
        if (ctx == null) throw new IllegalStateException("Missing TenantContext");

        jdbcTemplate.execute("SET LOCAL app.current_tenant = '" + ctx.tenantId() + "'");
        jdbcTemplate.execute("SET LOCAL app.current_country = '" + ctx.countryCode() + "'");
        if (ctx.legalEntityId() != null && !ctx.legalEntityId().isBlank()) {
            jdbcTemplate.execute("SET LOCAL app.current_legal_entity = '" + ctx.legalEntityId() + "'");
        }
        if ("SCHEMA".equalsIgnoreCase(ctx.tenantMode()) && ctx.tenantSchema() != null) {
            jdbcTemplate.execute("SET LOCAL search_path TO " + ctx.tenantSchema() + ", public");
        }
    }
}
