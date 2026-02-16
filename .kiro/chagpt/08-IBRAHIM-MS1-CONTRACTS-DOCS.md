# Documentation Contracts — Microservice #1 (erp-ms-tresorerie-backend)

---

## TENANT_CONTRACT.md

```markdown
# Tenant Contract — ERP Treasury Core

## Purpose
Transform this Spring service into Waangu 360 hybrid multi-tenant SaaS (POOLED/SCHEMA/DEDICATED_DB) with strict isolation.

## Required JWT Claims (Keycloak)

### Mandatory fields
- `tenant_id` (UUID) **REQUIRED**
- `tenant_mode`: `POOLED|SCHEMA|DEDICATED_DB` **REQUIRED**
- `billing_status`: `ACTIVE|TRIAL|PAST_DUE|SUSPENDED|TERMINATED` **REQUIRED**
- `enabled_modules`: must include `ERP_TREASURY_CORE` **REQUIRED**
- `subscription_id` (UUID) **REQUIRED**
- `legal_entity_id` (UUID) **REQUIRED** for write operations
- `country_code` (ISO-3166-1 alpha-2) **REQUIRED**
- `locale` (ISO 639-1) **REQUIRED**
- `user_id` (UUID) **REQUIRED**
- `correlation_id` (UUID) - auto-generated if missing

### Optional fields
- `supported_locales`: array of locale codes
- `roles`: array of role strings (e.g., `ERP_ADMIN`, `ACCOUNTANT`, `AUDITOR`)

## Hard Fail Rules

| Condition | HTTP Status | Error Code |
|-----------|-------------|------------|
| Missing `tenant_id` or `tenant_mode` | 401 | `MISSING_TENANT_CONTEXT` |
| `billing_status != ACTIVE/TRIAL` | 403 | `TENANT_SUSPENDED` |
| Module not in `enabled_modules` | 403 | `MODULE_DISABLED` |
| Missing `legal_entity_id` on write | 400 | `LEGAL_ENTITY_REQUIRED` |
| Invalid `country_code` format | 400 | `INVALID_COUNTRY_CODE` |
| Missing `locale` | 400 | `LOCALE_REQUIRED` |
| Mismatch `tenant_mode` ↔ DB routing | 500 | `ROUTING_MISMATCH` |

## DB Session Initialization (MANDATORY per transaction)

Every transaction MUST execute:

```sql
SELECT set_config('app.current_tenant', '<tenant_id>', true);
SELECT set_config('app.current_legal_entity', '<legal_entity_id>', true);
SELECT set_config('app.current_country', '<country_code>', true);
```

### SCHEMA mode additional:
```sql
SET LOCAL search_path TO tenant_<tenantIdNoDashes>, public;
```

## RLS Enforcement

All tenant-scoped tables have:
- `tenant_id UUID NOT NULL`
- `legal_entity_id UUID NOT NULL` (for financial tables)
- RLS enabled
- Policy: `tenant_id = current_setting('app.current_tenant')::uuid AND legal_entity_id = current_setting('app.current_legal_entity')::uuid`

## Tenant Modes

| Mode | Description | Routing |
|------|-------------|---------|
| **POOLED** | Shared tables + RLS | Default DataSource |
| **SCHEMA** | Dedicated schema per tenant | Default DS + `search_path` |
| **DEDICATED_DB** | Dedicated database per tenant | Tenant-specific DS (cached) |

## CI Gates

- ✅ Cross-tenant leak test must be 0
- ✅ Manifest schema validation must pass
- ✅ Migrations must apply cleanly
- ✅ RLS policies must exist on all tenant tables
- ✅ No platform class duplication
```

---

## I18N_CONTRACT.md

```markdown
# I18N Contract — Auto-translation Integration

## Rules

### Storage Convention
- **NO hardcoded business labels** in domain/API code
- Store two fields for every user-facing text:
  - `<field>_i18n_key` (TEXT NOT NULL) - translation key
  - `<field>_source` (TEXT NULL) - fallback value in source locale

### Translation Service

**Base URL**: Configured via `waangu.translationUrl`

#### Endpoints:
- `POST /i18n/keys` - Upsert source translation
- `GET /i18n/keys/{key}?locale={locale}` - Fetch translation

#### Request Format (upsert):
```json
{
  "key": "erp.treasury.bank_account.{uuid}.name",
  "locale": "fr",
  "text": "Compte principal BIF"
}
```

#### Response Format (fetch):
```json
{
  "key": "erp.treasury.bank_account.{uuid}.name",
  "locale": "en",
  "text": "Main BIF Account",
  "source_locale": "fr"
}
```

## Integration Pattern

### On entity creation:
1. Generate unique i18n key: `erp.{module}.{entity}.{uuid}.{field}`
2. Call Translation Service to upsert source
3. Store key in database
4. Return key to frontend (frontend fetches translation)

### On entity read:
- Return `<field>_i18n_key` and `<field>_source`
- Frontend or BFF resolves translation

## Failure Handling

### If Translation Service is down:
- Return `<field>_source` as fallback
- Emit **WARN** log with correlation_id
- Emit metric: `translation_service_unavailable`

### CI Gate
- ✅ No hardcoded labels in `src/main/java/.../domain` or `.../api`
- Scan for strings in business logic (manual review or regex scan)
```

---

## COPILOT_CONTRACT.md

```markdown
# Copilot Contract — Hybrid AI Integration

## Required Endpoint

Every microservice must expose:

```
GET /copilot/intents
```

### Response Format:
```json
{
  "module_id": "ERP_TREASURY_CORE",
  "intents": [
    {
      "name": "treasury.list_bank_accounts",
      "method": "GET",
      "path": "/api/erp/treasury/bank-accounts",
      "roles": ["ERP_TREASURY_READ", "ERP_ADMIN"]
    },
    {
      "name": "treasury.create_bank_account",
      "method": "POST",
      "path": "/api/erp/treasury/bank-accounts",
      "roles": ["ERP_TREASURY_WRITE", "ERP_ADMIN"]
    },
    {
      "name": "treasury.run_reconciliation",
      "method": "POST",
      "path": "/api/erp/treasury/reconciliations/run",
      "roles": ["ERP_TREASURY_WRITE", "ERP_ADMIN"]
    }
  ]
}
```

## Rules

1. **Each intent declares allowed roles** - RBAC enforced
2. **All write intents MUST write `audit_log`** with:
   - `action`: intent name
   - `correlation_id`: from request
   - `actor_user_id`: from JWT
   - `payload`: intent arguments
3. **Copilot requests respect**:
   - `tenant_id` / `legal_entity_id` isolation
   - RBAC (roles)
   - Idempotency (where applicable)
4. **No direct database access** - only via declared intents

## Security

- Copilot calls are authenticated via JWT (same as human users)
- Copilot user has role `ROLE_COPILOT` + specific module roles
- All actions audited with `actor_user_id` = copilot user

## CI Gate
- ✅ Endpoint `/copilot/intents` exists and returns valid JSON
- ✅ Each intent references existing endpoint
- ✅ Integration test: call intent → verify audit_log created
```

---

## SECURITY_MODEL.md

```markdown
# Security Model — ERP Treasury Core

## Authentication
- **Provider**: Keycloak OIDC
- **Token**: JWT (RS256)
- **Validation**: Spring Security OAuth2 Resource Server

## Authorization (RBAC)
- Roles extracted from JWT claim: `roles`
- Spring Security authorities: `ROLE_<role_name>`
- Enforced via `@PreAuthorize` or programmatic checks

### Example roles:
- `ERP_ADMIN`
- `ERP_TREASURY_READ`
- `ERP_TREASURY_WRITE`
- `ACCOUNTANT`
- `AUDITOR`

## Tenant Isolation

### PostgreSQL RLS (Row-Level Security)
- **Enabled** on all tenant-scoped tables
- **Policy**: `tenant_id = current_setting('app.current_tenant')::uuid`
- **Financial tables**: additional filter `legal_entity_id = current_setting('app.current_legal_entity')::uuid`

### Session Variables (per transaction)
- `app.current_tenant` (UUID)
- `app.current_legal_entity` (UUID)
- `app.current_country` (ISO-2)

### Hybrid Modes
- **POOLED**: RLS isolation on shared tables
- **SCHEMA**: Dedicated schema + `search_path`
- **DEDICATED_DB**: Dedicated database instance

## Audit Trail

### Append-only log
- Table: `audit_log`
- **Immutable**: no UPDATE/DELETE allowed
- **Hash chain**: `prev_hash` → `curr_hash` for integrity proof

### Required fields
- `tenant_id`, `legal_entity_id`, `actor_user_id`
- `action`, `entity_type`, `entity_id`
- `correlation_id`, `payload`, `occurred_at`

### CI Gate
- ✅ All mutations write `audit_log`
- ✅ Audit log tenant-scoped + RLS enabled

## Idempotency

### Purpose
- Prevent double-processing (double payment, double import)

### Implementation
- Table: `idempotency_key`
- Header: `Idempotency-Key` (UUID recommended)
- Same key + same request → same response (cached)
- Same key + different request → 409 CONFLICT

### CI Gate
- ✅ POST with same key returns same response
- ✅ POST with same key + different payload → 409

## Outbox Pattern

### Purpose
- Reliable event publishing for integrations (Engagement Hub, Payment Gateway)

### Implementation
- Table: `outbox_event`
- Events written in same transaction as business operation
- Separate worker publishes to Kafka/message bus

### CI Gate
- ✅ Critical actions emit outbox event
- ✅ No direct external service calls in transaction

## Network Security (Frantz)

### Allowed egress
- Keycloak (authentication)
- Tenant Registry (routing)
- Translation Service (i18n)
- Copilot Service (intents)
- Payment Gateway (if used)

### Denied
- Direct internet access
- Unencrypted connections
- Public database exposure

### TLS
- All internal service-to-service: TLS 1.3
- All client connections: TLS 1.3

## Secrets Management (Hugues)

- **Never** commit secrets to repository
- Use GitHub Actions Secrets for CI
- Use AWS Secrets Manager for prod
- Rotate secrets regularly (policy: 90 days)

## CI Gates
- ✅ Gitleaks scan passes (no secrets in code)
- ✅ No password= in YAML files
```

---

## docs/MIGRATION_ROLLBACK.md

```markdown
# Migration & Rollback Strategy

## Migration Approach

### Flyway (preferred)
- **Versioned migrations**: `V1__`, `V2__`, etc.
- **Idempotent**: can replay safely
- **Validated in CI** on ephemeral PostgreSQL

### CI Verification
1. `flyway:validate` - Check migration consistency
2. `flyway:migrate` - Apply on test database
3. RLS policy check - Verify all tables have policies

## Rollback Strategy

### Option A: Database Snapshot Restore (PREFERRED for prod)
- **Before migration**: Take snapshot (AWS RDS, Azure SQL)
- **If failure**: Restore snapshot
- **Downtime**: < 15 minutes (depends on size)

### Option B: Versioned Rollback Scripts
- Manual `DOWN` migrations (not supported by Flyway Community)
- Stored in `db/rollback/R{version}__*.sql`
- Tested in staging before prod

### Option C: Blue/Green Deployment
- Deploy to secondary environment
- Switch traffic if successful
- Rollback = switch back to primary

## Risk Assessment

### Low Risk Migrations
- Adding nullable columns
- Adding indexes
- Adding tables (new features)

### High Risk Migrations
- Dropping columns
- Changing column types
- Data transformations
- Large table alterations

### For High Risk:
1. Test in staging with production-like data
2. Dry-run in read-only replica
3. Schedule during maintenance window
4. Have DBA on call

## Multi-Tenant Considerations

### POOLED mode
- Migration runs once on shared database
- All tenants affected simultaneously
- **Test with multiple tenant contexts**

### SCHEMA mode
- Migration must run per schema
- Script: iterate over tenant schemas
- **Test schema creation + migration**

### DEDICATED_DB mode
- Migration runs per tenant database
- Orchestrated by platform (not microservice)
- **Test on sample dedicated DB**

## Audit Requirements

### Before migration:
- Document expected changes
- List affected tables/columns
- Estimate duration
- Identify rollback plan

### After migration:
- Verify RLS policies intact
- Verify data integrity (row counts, checksums)
- Verify application functionality (smoke tests)
- Archive migration logs

## CI Gate

- ✅ MIGRATION_PLAN filled when SQL changes
- ✅ ROLLBACK_PLAN filled when SQL changes
- ✅ Flyway validate + migrate succeeds on ephemeral DB
- ✅ RLS policies verified post-migration
```

---

## manifest.json

```json
{
  "module_id": "ERP_TREASURY_CORE",
  "type": "core",
  "service": "erp-ms-tresorerie-backend",
  "owner": "Ibrahim",
  "version": "1.0.0",
  "tenancy": {
    "modes": ["POOLED", "SCHEMA", "DEDICATED_DB"],
    "rls": true,
    "requires_tenant_context": true
  },
  "platform_contract": {
    "catalog": true,
    "subscription": true,
    "billing": true,
    "entitlements": true
  },
  "capabilities": {
    "multi_country": true,
    "multi_company": true,
    "translation": true,
    "copilot": true,
    "audit_ready": true,
    "idempotency": true,
    "outbox": true
  },
  "dependencies": {
    "core": ["erp.core"],
    "platform": ["saas.catalog", "saas.subscription", "saas.billing", "tenant.registry"]
  },
  "api": {
    "base_path": "/api/erp/treasury"
  },
  "audit_readiness": {
    "isa_supported": ["200", "300", "315", "330", "500", "700"],
    "isqm": true,
    "read_only_audit_api": true,
    "evidence_linking": true
  }
}
```

---

## manifest.schema.json

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "type": "object",
  "required": [
    "module_id",
    "type",
    "service",
    "owner",
    "version",
    "tenancy",
    "platform_contract",
    "capabilities",
    "api"
  ],
  "properties": {
    "module_id": {
      "type": "string",
      "minLength": 3,
      "pattern": "^[A-Z_]+$"
    },
    "type": {
      "enum": ["core", "plugin"]
    },
    "service": {
      "type": "string",
      "minLength": 3
    },
    "owner": {
      "type": "string",
      "minLength": 2
    },
    "version": {
      "type": "string",
      "pattern": "^[0-9]+\\.[0-9]+\\.[0-9]+(-[A-Za-z0-9\\.\\-]+)?$"
    },
    "tenancy": {
      "type": "object",
      "required": ["modes", "rls", "requires_tenant_context"],
      "properties": {
        "modes": {
          "type": "array",
          "minItems": 1,
          "items": {
            "enum": ["POOLED", "SCHEMA", "DEDICATED_DB"]
          }
        },
        "rls": {
          "const": true
        },
        "requires_tenant_context": {
          "const": true
        }
      }
    },
    "platform_contract": {
      "type": "object",
      "required": ["catalog", "subscription", "billing", "entitlements"],
      "properties": {
        "catalog": { "const": true },
        "subscription": { "const": true },
        "billing": { "const": true },
        "entitlements": { "const": true }
      }
    },
    "capabilities": {
      "type": "object",
      "required": [
        "multi_country",
        "multi_company",
        "translation",
        "copilot",
        "audit_ready",
        "idempotency",
        "outbox"
      ],
      "properties": {
        "multi_country": { "type": "boolean" },
        "multi_company": { "type": "boolean" },
        "translation": { "type": "boolean" },
        "copilot": { "type": "boolean" },
        "audit_ready": { "type": "boolean" },
        "idempotency": { "type": "boolean" },
        "outbox": { "type": "boolean" }
      }
    },
    "api": {
      "type": "object",
      "required": ["base_path"],
      "properties": {
        "base_path": {
          "type": "string",
          "pattern": "^/api/"
        }
      }
    }
  }
}
```
