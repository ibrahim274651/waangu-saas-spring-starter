-- ============================================================================
-- Waangu 360 SaaS Platform - Row Level Security (RLS) Policies
-- Version: V2
-- Description: Enables RLS on all platform tables for tenant isolation
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ENABLE RLS ON ALL TABLES
-- ----------------------------------------------------------------------------

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_key ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_event ENABLE ROW LEVEL SECURITY;

-- Force RLS even for table owners (security best practice)
ALTER TABLE audit_log FORCE ROW LEVEL SECURITY;
ALTER TABLE idempotency_key FORCE ROW LEVEL SECURITY;
ALTER TABLE outbox_event FORCE ROW LEVEL SECURITY;

-- ----------------------------------------------------------------------------
-- RLS POLICIES FOR AUDIT_LOG
-- ----------------------------------------------------------------------------

DROP POLICY IF EXISTS rls_audit_log_tenant ON audit_log;
CREATE POLICY rls_audit_log_tenant ON audit_log
    USING (
        tenant_id = COALESCE(
            NULLIF(current_setting('app.current_tenant', true), '')::UUID,
            '00000000-0000-0000-0000-000000000000'::UUID
        )
        AND legal_entity_id = COALESCE(
            NULLIF(current_setting('app.current_legal_entity', true), '')::UUID,
            tenant_id
        )
    );

COMMENT ON POLICY rls_audit_log_tenant ON audit_log IS 
    'Restricts audit_log access to current tenant and legal entity';

-- ----------------------------------------------------------------------------
-- RLS POLICIES FOR IDEMPOTENCY_KEY
-- ----------------------------------------------------------------------------

DROP POLICY IF EXISTS rls_idempotency_key_tenant ON idempotency_key;
CREATE POLICY rls_idempotency_key_tenant ON idempotency_key
    USING (
        tenant_id = COALESCE(
            NULLIF(current_setting('app.current_tenant', true), '')::UUID,
            '00000000-0000-0000-0000-000000000000'::UUID
        )
        AND legal_entity_id = COALESCE(
            NULLIF(current_setting('app.current_legal_entity', true), '')::UUID,
            tenant_id
        )
    );

COMMENT ON POLICY rls_idempotency_key_tenant ON idempotency_key IS 
    'Restricts idempotency_key access to current tenant and legal entity';

-- ----------------------------------------------------------------------------
-- RLS POLICIES FOR OUTBOX_EVENT
-- ----------------------------------------------------------------------------

DROP POLICY IF EXISTS rls_outbox_event_tenant ON outbox_event;
CREATE POLICY rls_outbox_event_tenant ON outbox_event
    USING (
        tenant_id = COALESCE(
            NULLIF(current_setting('app.current_tenant', true), '')::UUID,
            '00000000-0000-0000-0000-000000000000'::UUID
        )
        AND legal_entity_id = COALESCE(
            NULLIF(current_setting('app.current_legal_entity', true), '')::UUID,
            tenant_id
        )
    );

COMMENT ON POLICY rls_outbox_event_tenant ON outbox_event IS 
    'Restricts outbox_event access to current tenant and legal entity';

-- ----------------------------------------------------------------------------
-- HELPER FUNCTION FOR SETTING TENANT CONTEXT
-- ----------------------------------------------------------------------------

CREATE OR REPLACE FUNCTION set_tenant_context(
    p_tenant_id UUID,
    p_legal_entity_id UUID DEFAULT NULL,
    p_country_code VARCHAR(2) DEFAULT NULL
) RETURNS VOID AS $$
BEGIN
    PERFORM set_config('app.current_tenant', p_tenant_id::TEXT, true);
    
    IF p_legal_entity_id IS NOT NULL THEN
        PERFORM set_config('app.current_legal_entity', p_legal_entity_id::TEXT, true);
    ELSE
        PERFORM set_config('app.current_legal_entity', p_tenant_id::TEXT, true);
    END IF;
    
    IF p_country_code IS NOT NULL THEN
        PERFORM set_config('app.current_country', p_country_code, true);
    END IF;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION set_tenant_context IS 
    'Helper function to set tenant context for RLS policies within a transaction';
