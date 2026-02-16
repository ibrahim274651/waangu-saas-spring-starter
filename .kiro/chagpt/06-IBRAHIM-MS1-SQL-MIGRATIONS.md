# SQL Migrations — Microservice #1 (erp-ms-tresorerie-backend)

**Database**: PostgreSQL 16
**Migration Tool**: Flyway

---

## V1__treasury_tables.sql

```sql
-- Treasury Bank Accounts
CREATE TABLE treasury_bank_account (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    bank_name TEXT NOT NULL,
    iban TEXT NULL,
    account_number TEXT NULL,
    
    -- i18n
    name_i18n_key TEXT NOT NULL,
    name_source TEXT NULL,
    
    -- state
    is_active BOOLEAN NOT NULL DEFAULT true,
    posted_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- audit
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NULL,
    updated_by UUID NULL
);

-- Bank Statements
CREATE TABLE treasury_bank_statement (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL,
    bank_account_id UUID NOT NULL REFERENCES treasury_bank_account(id),
    statement_date DATE NOT NULL,
    source_type TEXT NOT NULL,  -- CSV, OFX, API
    source_ref TEXT NULL,       -- file hash or upstream id
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL
);

-- Transactions
CREATE TABLE treasury_transaction (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL,
    bank_statement_id UUID NOT NULL REFERENCES treasury_bank_statement(id),
    txn_date DATE NOT NULL,
    amount NUMERIC(18,2) NOT NULL,
    currency_code CHAR(3) NOT NULL,
    
    -- i18n
    description_i18n_key TEXT NOT NULL,
    description_source TEXT NULL,
    
    -- reconciliation
    matched BOOLEAN NOT NULL DEFAULT false,
    match_ref TEXT NULL,
    
    -- state
    posted_at TIMESTAMPTZ NULL,
    version BIGINT NOT NULL DEFAULT 0,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Reconciliations
CREATE TABLE treasury_reconciliation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL,
    bank_account_id UUID NOT NULL REFERENCES treasury_bank_account(id),
    status TEXT NOT NULL DEFAULT 'DRAFT',  -- DRAFT, RUNNING, DONE, FAILED
    from_date DATE NOT NULL,
    to_date DATE NOT NULL,
    result JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL
);

-- Cash Boxes
CREATE TABLE treasury_cashbox (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL,
    
    -- i18n
    name_i18n_key TEXT NOT NULL,
    name_source TEXT NULL,
    
    currency_code CHAR(3) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL
);

-- Chart of Accounts (COA) - Core
CREATE TABLE coa_accounts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL,
    code TEXT NOT NULL,
    
    -- i18n
    name_i18n_key TEXT NOT NULL,
    name_source TEXT NULL,
    
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL,
    updated_at TIMESTAMPTZ NULL,
    updated_by UUID NULL
);

-- Accounting Settings (localized)
CREATE TABLE accounting_settings (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL,
    setting_key TEXT NOT NULL,
    value JSONB NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by UUID NOT NULL
);
```

---

## V2__audit_outbox_idempotency.sql

```sql
-- Audit Log (append-only + hash chain)
CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    actor_user_id UUID NOT NULL,
    
    action TEXT NOT NULL,
    entity_type TEXT NOT NULL,
    entity_id UUID NOT NULL,
    
    correlation_id TEXT NOT NULL,
    payload JSONB NOT NULL,
    
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- hash chain (audit integrity)
    prev_hash TEXT NULL,
    curr_hash TEXT NULL
);

-- Outbox Pattern (integration events)
CREATE TABLE outbox_event (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    event_type TEXT NOT NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL DEFAULT 'NEW',  -- NEW, SENT, FAILED
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ NULL
);

-- Idempotency Keys (anti double-payment)
CREATE TABLE idempotency_key (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    key TEXT NOT NULL,
    request_hash TEXT NOT NULL,
    response JSONB NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

---

## V3__rls.sql

```sql
-- Enable RLS on all tenant-scoped tables
ALTER TABLE treasury_bank_account ENABLE ROW LEVEL SECURITY;
ALTER TABLE treasury_bank_statement ENABLE ROW LEVEL SECURITY;
ALTER TABLE treasury_transaction ENABLE ROW LEVEL SECURITY;
ALTER TABLE treasury_reconciliation ENABLE ROW LEVEL SECURITY;
ALTER TABLE treasury_cashbox ENABLE ROW LEVEL SECURITY;
ALTER TABLE coa_accounts ENABLE ROW LEVEL SECURITY;
ALTER TABLE accounting_settings ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE outbox_event ENABLE ROW LEVEL SECURITY;
ALTER TABLE idempotency_key ENABLE ROW LEVEL SECURITY;

-- Policies: tenant + legal_entity scoped (for financial tables)
CREATE POLICY rls_treasury_bank_account ON treasury_bank_account
USING (
    tenant_id = current_setting('app.current_tenant')::uuid
    AND legal_entity_id = current_setting('app.current_legal_entity')::uuid
);

CREATE POLICY rls_treasury_bank_statement ON treasury_bank_statement
USING (
    tenant_id = current_setting('app.current_tenant')::uuid
    AND legal_entity_id = current_setting('app.current_legal_entity')::uuid
);

CREATE POLICY rls_treasury_transaction ON treasury_transaction
USING (
    tenant_id = current_setting('app.current_tenant')::uuid
    AND legal_entity_id = current_setting('app.current_legal_entity')::uuid
);

CREATE POLICY rls_treasury_reconciliation ON treasury_reconciliation
USING (
    tenant_id = current_setting('app.current_tenant')::uuid
    AND legal_entity_id = current_setting('app.current_legal_entity')::uuid
);

CREATE POLICY rls_treasury_cashbox ON treasury_cashbox
USING (
    tenant_id = current_setting('app.current_tenant')::uuid
    AND legal_entity_id = current_setting('app.current_legal_entity')::uuid
);

CREATE POLICY rls_coa_accounts ON coa_accounts
USING (
    tenant_id = current_setting('app.current_tenant')::uuid
    AND legal_entity_id = current_setting('app.current_legal_entity')::uuid
);

CREATE POLICY rls_accounting_settings ON accounting_settings
USING (
    tenant_id = current_setting('app.current_tenant')::uuid
    AND legal_entity_id = current_setting('app.current_legal_entity')::uuid
);

-- Audit/outbox/idempotency: tenant-scoped only (not legal_entity because system-wide per tenant)
CREATE POLICY rls_audit_log ON audit_log
USING (tenant_id = current_setting('app.current_tenant')::uuid);

CREATE POLICY rls_outbox_event ON outbox_event
USING (tenant_id = current_setting('app.current_tenant')::uuid);

CREATE POLICY rls_idempotency_key ON idempotency_key
USING (tenant_id = current_setting('app.current_tenant')::uuid);
```

---

## V4__indexes_constraints.sql

```sql
-- Performance indexes (tenant + legal_entity + country scoping)
CREATE INDEX idx_tba_scope ON treasury_bank_account(tenant_id, legal_entity_id, country_code);
CREATE UNIQUE INDEX uq_tba_iban ON treasury_bank_account(tenant_id, legal_entity_id, iban) 
    WHERE iban IS NOT NULL AND is_active = true;

CREATE INDEX idx_tbs_scope ON treasury_bank_statement(tenant_id, legal_entity_id, statement_date);

CREATE INDEX idx_ttxn_scope ON treasury_transaction(tenant_id, legal_entity_id, txn_date);
CREATE INDEX idx_ttxn_match ON treasury_transaction(tenant_id, legal_entity_id, matched);

CREATE INDEX idx_trec_scope ON treasury_reconciliation(tenant_id, legal_entity_id, status);

CREATE INDEX idx_cashbox_scope ON treasury_cashbox(tenant_id, legal_entity_id, country_code);

CREATE INDEX idx_coa_scope ON coa_accounts(tenant_id, legal_entity_id, country_code);
CREATE UNIQUE INDEX uq_coa_code ON coa_accounts(tenant_id, legal_entity_id, code) 
    WHERE is_active = true;

CREATE INDEX idx_settings_scope ON accounting_settings(tenant_id, legal_entity_id, country_code, setting_key);

-- Audit/system tables indexes
CREATE INDEX idx_audit_scope ON audit_log(tenant_id, legal_entity_id, occurred_at);
CREATE INDEX idx_audit_entity ON audit_log(tenant_id, entity_type, entity_id);

CREATE INDEX idx_outbox_status ON outbox_event(tenant_id, status, created_at);

CREATE UNIQUE INDEX uq_idempo ON idempotency_key(tenant_id, legal_entity_id, key);

-- Foreign key constraints (if not already implicit via REFERENCES)
ALTER TABLE treasury_transaction 
    ADD CONSTRAINT fk_ttxn_statement 
    FOREIGN KEY (bank_statement_id) REFERENCES treasury_bank_statement(id);

ALTER TABLE treasury_bank_statement 
    ADD CONSTRAINT fk_tbs_account 
    FOREIGN KEY (bank_account_id) REFERENCES treasury_bank_account(id);

ALTER TABLE treasury_reconciliation 
    ADD CONSTRAINT fk_trec_account 
    FOREIGN KEY (bank_account_id) REFERENCES treasury_bank_account(id);
```

---

## CI Script: RLS Verification (add to migrations job)

```bash
#!/bin/bash
# check_rls_policies.sh

DB_URL="${DB_URL:-postgresql://postgres:postgres@localhost:5432/erp_test}"

echo "Checking RLS policies on tenant-scoped tables..."

missing=$(psql "$DB_URL" -t -A <<'SQL'
WITH t AS (
    SELECT c.relname AS table_name
    FROM pg_class c
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'public'
      AND c.relkind = 'r'
      AND (
          c.relname LIKE 'treasury_%'
          OR c.relname IN ('coa_accounts', 'accounting_settings', 'audit_log', 'outbox_event', 'idempotency_key')
      )
),
p AS (
    SELECT polrelid::regclass::text AS table_name
    FROM pg_policy
)
SELECT t.table_name
FROM t
LEFT JOIN p ON p.table_name = t.table_name
WHERE p.table_name IS NULL;
SQL
)

if [ -n "$missing" ]; then
    echo "❌ MISSING RLS POLICIES ON:"
    echo "$missing"
    exit 1
fi

echo "✅ All tenant-scoped tables have RLS policies"
```
