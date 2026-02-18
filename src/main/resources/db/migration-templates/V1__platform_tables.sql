-- ============================================================================
-- Waangu 360 SaaS Platform - Core Tables Migration
-- Version: V1
-- Description: Creates core platform tables for multi-tenant infrastructure
-- ============================================================================

-- ----------------------------------------------------------------------------
-- AUDIT LOG TABLE (Hash-chain for tamper-evident audit trail)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    correlation_id VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    prev_hash VARCHAR(64),
    curr_hash VARCHAR(64) NOT NULL,
    
    CONSTRAINT audit_log_action_not_empty CHECK (action <> ''),
    CONSTRAINT audit_log_entity_type_not_empty CHECK (entity_type <> '')
);

CREATE INDEX IF NOT EXISTS idx_audit_log_tenant ON audit_log(tenant_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_tenant_legal ON audit_log(tenant_id, legal_entity_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_correlation ON audit_log(correlation_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_occurred ON audit_log(occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity ON audit_log(entity_type, entity_id);

COMMENT ON TABLE audit_log IS 'Tamper-evident audit trail with SHA-256 hash chain';
COMMENT ON COLUMN audit_log.prev_hash IS 'SHA-256 hash of previous audit entry (null for first entry)';
COMMENT ON COLUMN audit_log.curr_hash IS 'SHA-256 hash of current entry including prev_hash';

-- ----------------------------------------------------------------------------
-- IDEMPOTENCY KEY TABLE (Anti double-spend / duplicate prevention)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS idempotency_key (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL DEFAULT (NOW() + INTERVAL '24 hours'),
    
    CONSTRAINT idempotency_key_not_empty CHECK (key <> ''),
    CONSTRAINT idempotency_status_valid CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_idempotency_key 
    ON idempotency_key(tenant_id, legal_entity_id, key);
CREATE INDEX IF NOT EXISTS idx_idempotency_expires 
    ON idempotency_key(expires_at) WHERE status = 'COMPLETED';

COMMENT ON TABLE idempotency_key IS 'Idempotency tracking for financial operations (anti double-spend)';
COMMENT ON COLUMN idempotency_key.request_hash IS 'SHA-256 hash of request payload for conflict detection';

-- ----------------------------------------------------------------------------
-- OUTBOX EVENT TABLE (Transactional outbox pattern for reliable messaging)
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS outbox_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at TIMESTAMPTZ,
    retry_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    
    CONSTRAINT outbox_event_type_not_empty CHECK (event_type <> ''),
    CONSTRAINT outbox_status_valid CHECK (status IN ('NEW', 'PROCESSING', 'SENT', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_outbox_pending 
    ON outbox_event(status, created_at) WHERE status IN ('NEW', 'FAILED');
CREATE INDEX IF NOT EXISTS idx_outbox_tenant 
    ON outbox_event(tenant_id, legal_entity_id);

COMMENT ON TABLE outbox_event IS 'Transactional outbox for reliable event publishing';
COMMENT ON COLUMN outbox_event.retry_count IS 'Number of delivery attempts for failed events';
