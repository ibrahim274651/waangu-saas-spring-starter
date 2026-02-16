# Microservice #3 — erp-ms-comptabilite-analytic

**Module**: Comptabilité Analytique — Axes / Dimensions / Allocations
**Type**: CORE ERP
**Owner**: Ibrahim

---

## Classification

| Élément | Valeur |
|---------|--------|
| Nom | erp-ms-comptabilite-analytic |
| Module ID | `ERP_ANALYTIC_CORE` |
| Type | CORE ERP |
| Owner | Ibrahim |
| Désactivable | ❌ NON |
| Facturable | ❌ NON (inclus CORE) |
| Dépend de | ERP Core + GL Core |
| Supporte plugins | ✅ (IFRS analytics, IPSAS budgets) |

---

## Objectif

Fournir un moteur analytique multi-axes pour:
1. **Centres de coûts**
2. **Projets**
3. **Départements**
4. **Axes personnalisés**
5. **Allocations automatiques** (ventilation écritures GL)

---

## Périmètre fonctionnel

### Inclus (OBLIGATOIRE)
- Définition d'axes analytiques (dimensions personnalisables)
- Dimensions (valeurs par axe)
- Règles d'allocation (JSON-based, versionnées)
- Exécution d'allocations sur écritures GL
- Traçabilité allocations (audit trail)

### Exclu (INTERDIT)
- Règles sectorielles spécifiques → PLUGINS
- États analytiques légaux → PLUGINS

---

## Tables minimales

1. **ana_axis** (Axes analytiques)
2. **ana_dimension** (Valeurs d'axes)
3. **ana_allocation_rule** (Règles d'allocation)
4. **ana_posting** (Résultats d'allocations)
5. **audit_log** (Append-only)
6. **outbox_event**
7. **idempotency_key**

---

## Modèle de données

### ana_axis
```sql
CREATE TABLE ana_axis (
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
    created_by UUID NOT NULL
);
```

### ana_dimension
```sql
CREATE TABLE ana_dimension (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    axis_id UUID NOT NULL REFERENCES ana_axis(id),
    code TEXT NOT NULL,
    
    -- i18n
    name_i18n_key TEXT NOT NULL,
    name_source TEXT NULL,
    
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL
);
```

### ana_allocation_rule
```sql
CREATE TABLE ana_allocation_rule (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL,
    rule_code TEXT NOT NULL,
    
    -- i18n
    description_i18n_key TEXT NOT NULL,
    description_source TEXT NULL,
    
    -- rule definition (JSON)
    rule JSONB NOT NULL,
    
    -- state
    version BIGINT NOT NULL DEFAULT 0,
    posted_at TIMESTAMPTZ NULL,  -- if posted => IMMUTABLE
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL
);
```

### ana_posting
```sql
CREATE TABLE ana_posting (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    legal_entity_id UUID NOT NULL,
    country_code CHAR(2) NOT NULL,
    
    source_entry_id UUID NOT NULL,  -- reference to GL entry
    allocation_rule_id UUID NOT NULL REFERENCES ana_allocation_rule(id),
    
    result JSONB NOT NULL,  -- allocation result
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID NOT NULL
);
```

---

## API Endpoints

Base path: `/api/erp/analytic`

### Axes
- `POST /axes` (Idempotency-Key required)
- `GET /axes`
- `GET /axes/{id}`

### Dimensions
- `POST /dimensions` (Idempotency-Key recommended)
- `GET /dimensions?axis_id=...`
- `GET /dimensions/{id}`

### Allocation Rules
- `POST /allocation-rules` (Idempotency-Key recommended)
- `GET /allocation-rules`
- `GET /allocation-rules/{id}`
- `POST /allocation-rules/{id}/post` (lock rule)

### Allocations (execution)
- `POST /allocations/run` (Idempotency-Key **REQUIRED**)
  - Body: `{ "sourceEntryId": "uuid", "allocationRuleId": "uuid" }`
  - Returns: posting result

### Postings
- `GET /postings/{id}`
- `GET /postings?source_entry_id=...`

---

## Guards spécifiques

### 1. Posted Rule Immutability
```java
private void assertRuleNotPosted(UUID ruleId) {
    AllocationRuleResponse rule = ruleRepo.get(ruleId);
    if (rule.postedAt() != null) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "IMMUTABLE_POSTED");
    }
}
```

### 2. Source Entry Validation
```java
private void assertEntryExists(UUID sourceEntryId) {
    // Call GL microservice or check via shared view (if allowed)
    // For now: assume validated upstream or via event
    // Guard: entry must be POSTED (not DRAFT)
}
```

### 3. Allocation Idempotency (CRITICAL)
- Same `sourceEntryId` + `allocationRuleId` → same result
- Prevents double-allocation

---

## Events normalisés

### ANALYTIC.AXIS.CREATED
```json
{
  "event": "ANALYTIC.AXIS.CREATED",
  "tenant_id": "uuid",
  "legal_entity_id": "uuid",
  "entity_id": "uuid",
  "timestamp": "ISO-8601",
  "correlation_id": "uuid",
  "payload": {
    "code": "COST_CENTER",
    "name_key": "erp.analytic.axis.{uuid}.name"
  }
}
```

### ANALYTIC.ALLOCATION.RUN
```json
{
  "event": "ANALYTIC.ALLOCATION.RUN",
  "tenant_id": "uuid",
  "legal_entity_id": "uuid",
  "entity_id": "uuid",
  "timestamp": "ISO-8601",
  "correlation_id": "uuid",
  "payload": {
    "posting_id": "uuid",
    "source_entry_id": "uuid",
    "rule_id": "uuid",
    "result_summary": {}
  }
}
```

---

## Tests obligatoires supplémentaires

### 1. Posted rule immutability
- Post allocation rule
- Attempt to modify → **409**

### 2. Allocation idempotency
- Run allocation with Idempotency-Key
- Call again with same key → same response
- Call with same key + different payload → **409**

### 3. Performance test (RECOMMENDED)
- Seed 10,000 ana_posting records
- Run allocation queries
- Assert response time < threshold (e.g., 3s)

### 4. Multi-axis allocation
- Create 3 axes (cost center, project, department)
- Create dimensions per axis
- Run allocation with rule referencing all axes
- Verify result contains all axes

---

## RLS Policies

```sql
ALTER TABLE ana_axis ENABLE ROW LEVEL SECURITY;
ALTER TABLE ana_dimension ENABLE ROW LEVEL SECURITY;
ALTER TABLE ana_allocation_rule ENABLE ROW LEVEL SECURITY;
ALTER TABLE ana_posting ENABLE ROW LEVEL SECURITY;

CREATE POLICY rls_ana_axis ON ana_axis
USING (
    tenant_id = current_setting('app.current_tenant')::uuid
    AND legal_entity_id = current_setting('app.current_legal_entity')::uuid
);

CREATE POLICY rls_ana_dimension ON ana_dimension
USING (
    tenant_id = current_setting('app.current_tenant')::uuid
    AND legal_entity_id = current_setting('app.current_legal_entity')::uuid
);

CREATE POLICY rls_ana_allocation_rule ON ana_allocation_rule
USING (
    tenant_id = current_setting('app.current_tenant')::uuid
    AND legal_entity_id = current_setting('app.current_legal_entity')::uuid
);

CREATE POLICY rls_ana_posting ON ana_posting
USING (
    tenant_id = current_setting('app.current_tenant')::uuid
    AND legal_entity_id = current_setting('app.current_legal_entity')::uuid
);
```

---

## Livrables (5 obligatoires)

Identiques au Microservice #1 + #2:

1. **PR Spring** (starter + services + repos + guards)
2. **Migrations SQL** (tables + RLS + indexes)
3. **Manifest validé** (AJV gate)
4. **CI green** (unit/integration/cross_tenant/migrations + perf optionnel)
5. **Docs contracts**

---

## Definition of Done

✅ Tous les critères des microservices #1 et #2
✅ Axes et dimensions tenant-scopés + RLS
✅ Allocation rules versionnées + immutabilité
✅ Idempotency sur allocations (CRITICAL)
✅ Outbox events pour intégrations
✅ Performance acceptable sur allocations volumineuses (si perf test activé)
✅ Aucune dépendance circulaire avec GL (consommer via events ou API read)
