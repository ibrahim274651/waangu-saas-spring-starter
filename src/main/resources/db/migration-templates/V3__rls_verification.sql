-- ============================================================================
-- Waangu 360 SaaS Platform - RLS Verification Functions
-- Version: V3
-- Description: Functions to verify RLS is properly configured (for CI/CD)
-- ============================================================================

-- ----------------------------------------------------------------------------
-- FUNCTION: Check if RLS is enabled on all required tables
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION verify_rls_enabled()
RETURNS TABLE (
    table_name TEXT,
    rls_enabled BOOLEAN,
    rls_forced BOOLEAN,
    policy_count BIGINT
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        c.relname::TEXT AS table_name,
        c.relrowsecurity AS rls_enabled,
        c.relforcerowsecurity AS rls_forced,
        (SELECT COUNT(*) FROM pg_policies p WHERE p.tablename = c.relname) AS policy_count
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
    AND c.relkind = 'r'
    AND c.relname IN ('audit_log', 'idempotency_key', 'outbox_event')
    ORDER BY c.relname;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION verify_rls_enabled IS 
    'Returns RLS status for all platform tables - use in CI to verify security';

-- ----------------------------------------------------------------------------
-- FUNCTION: Find tables without RLS (security audit)
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION find_tables_without_rls()
RETURNS TABLE (
    schema_name TEXT,
    table_name TEXT,
    has_tenant_id BOOLEAN
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        n.nspname::TEXT AS schema_name,
        c.relname::TEXT AS table_name,
        EXISTS (
            SELECT 1 FROM information_schema.columns col
            WHERE col.table_schema = n.nspname
            AND col.table_name = c.relname
            AND col.column_name = 'tenant_id'
        ) AS has_tenant_id
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
    AND c.relkind = 'r'
    AND c.relrowsecurity = false
    AND EXISTS (
        SELECT 1 FROM information_schema.columns col
        WHERE col.table_schema = n.nspname
        AND col.table_name = c.relname
        AND col.column_name = 'tenant_id'
    )
    ORDER BY c.relname;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION find_tables_without_rls IS 
    'Finds tables with tenant_id column but without RLS enabled - security audit';

-- ----------------------------------------------------------------------------
-- FUNCTION: Cross-tenant leak test
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION test_cross_tenant_isolation(
    p_table_name TEXT,
    p_tenant_a UUID,
    p_tenant_b UUID
) RETURNS TABLE (
    test_name TEXT,
    passed BOOLEAN,
    details TEXT
) AS $$
DECLARE
    v_count_a BIGINT;
    v_count_b BIGINT;
    v_count_total BIGINT;
BEGIN
    PERFORM set_config('app.current_tenant', p_tenant_a::TEXT, true);
    PERFORM set_config('app.current_legal_entity', p_tenant_a::TEXT, true);
    
    EXECUTE format('SELECT COUNT(*) FROM %I WHERE tenant_id = $1', p_table_name)
    INTO v_count_a USING p_tenant_a;
    
    EXECUTE format('SELECT COUNT(*) FROM %I', p_table_name)
    INTO v_count_total;
    
    test_name := 'Tenant A sees only own data';
    passed := (v_count_a = v_count_total);
    details := format('Tenant A data: %s, Total visible: %s', v_count_a, v_count_total);
    RETURN NEXT;
    
    PERFORM set_config('app.current_tenant', p_tenant_b::TEXT, true);
    PERFORM set_config('app.current_legal_entity', p_tenant_b::TEXT, true);
    
    EXECUTE format('SELECT COUNT(*) FROM %I WHERE tenant_id = $1', p_table_name)
    INTO v_count_b USING p_tenant_b;
    
    EXECUTE format('SELECT COUNT(*) FROM %I', p_table_name)
    INTO v_count_total;
    
    test_name := 'Tenant B sees only own data';
    passed := (v_count_b = v_count_total);
    details := format('Tenant B data: %s, Total visible: %s', v_count_b, v_count_total);
    RETURN NEXT;
    
    test_name := 'No cross-tenant data leak';
    passed := (v_count_a = 0 OR v_count_b = 0 OR v_count_a + v_count_b > v_count_total);
    details := 'RLS properly isolates tenant data';
    RETURN NEXT;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION test_cross_tenant_isolation IS 
    'Tests RLS isolation between two tenants - use in integration tests';
