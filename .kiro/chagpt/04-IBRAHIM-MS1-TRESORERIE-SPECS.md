# Microservice #1 — erp-ms-tresorerie-backend

**Module**: Comptabilité générale - Plan comptable / Trésorerie
**Type**: CORE ERP
**Owner**: Ibrahim
**Status**: NON OPTIONNEL

---

## Classification

| Élément | Valeur |
|---------|--------|
| Nom | erp-ms-tresorerie-backend |
| Module ID | `ERP_TREASURY_CORE` |
| Type | CORE ERP (NON OPTIONNEL) |
| Owner | Ibrahim |
| Désactivable | ❌ NON |
| Facturable | ❌ NON (inclus CORE) |
| Dépend de | ERP Core uniquement |
| Supporte plugins | ✅ (OHADA, IFRS, TVA, etc.) |

---

## Mission d'Ibrahim

Transformer le moteur comptable/trésorerie existant pour qu'il soit:
1. Multi-tenant hybride (POOLED/SCHEMA/DEDICATED_DB)
2. Hiérarchique (tenant → sous-tenant)
3. Audit-ready (ISA/ISQM)
4. Extensible par plugins comptables
5. Compatible Catalogue SaaS (sans être vendable)

⚠️ **Aucune logique fiscale, OHADA, TVA, IFRS n'est autorisée ici.**

---

## Périmètre fonctionnel STRICT (CORE ONLY)

### Inclus (OBLIGATOIRE)
- Plan comptable générique template
- Gestion des journaux (définitions, séquences)
- Gestion des exercices/périodes (création, verrouillage)
- Banques / Caisses (référentiel)
- Transactions bancaires
- Rapprochements bancaires techniques
- Exports techniques (pas fiscaux)

### Exclu (INTERDIT)
- TVA / Déclarations fiscales
- États financiers légaux
- Règles OHADA/IFRS/GAAP
- Consolidation
- Intégration PSP externe (tout paiement passe par Payment Gateway Waangu)

---

## Architecture multi-tenant imposée

### Modèle de données (OBLIGATOIRE)

Toutes les tables doivent contenir:
```sql
tenant_id UUID NOT NULL,
legal_entity_id UUID NOT NULL,
country_code CHAR(2) NOT NULL,
-- i18n fields
name_i18n_key TEXT NOT NULL,
name_source TEXT NULL,
-- audit fields
created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
created_by UUID NOT NULL,
updated_at TIMESTAMPTZ,
updated_by UUID,
version BIGINT NOT NULL DEFAULT 0
```

### Isolation PostgreSQL (RLS — PREUVE OBLIGATOIRE)

```sql
ALTER TABLE treasury_bank_account ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_treasury_bank_account
ON treasury_bank_account
USING (
  tenant_id = current_setting('app.current_tenant')::uuid
  AND legal_entity_id = current_setting('app.current_legal_entity')::uuid
);
```

**Règles absolues**:
- RLS activé par défaut
- Impossible de désactiver en prod
- Test automatisé: Tenant A ≠ Tenant B → 0 ligne retournée

### Modes supportés

| Mode | Description |
|------|-------------|
| POOLED (Mutualisé) | Tables partagées + RLS |
| SCHEMA | 1 schéma / tenant |
| DEDICATED_DB | 1 DB / tenant |

👉 Le mode est injecté par la plateforme, jamais décidé par le service.

---

## Contrat tenant-context (obligation)

Le service refuse toute requête sans ce contexte valide:

```json
{
  "tenant_id": "uuid",
  "tenant_mode": "POOLED|SCHEMA|DEDICATED_DB",
  "legal_entity_id": "uuid",
  "country_code": "BI",
  "locale": "fr",
  "supported_locales": ["fr","en","sw"],
  "billing_status": "ACTIVE|TRIAL|SUSPENDED|TERMINATED",
  "enabled_modules": ["ERP_TREASURY_CORE"],
  "subscription_id": "uuid",
  "roles": ["ERP_ADMIN","ACCOUNTANT","AUDITOR"],
  "user_id": "uuid",
  "correlation_id": "uuid"
}
```

### Règles hard-fail
- ❌ `tenant_id` absent → 401
- ❌ `billing_status != ACTIVE/TRIAL` → 403 TENANT_SUSPENDED
- ❌ module non actif → 403 MODULE_DISABLED
- ❌ incohérence `tenant_mode` / DB → 500 FAIL HARD
- ❌ `country_code` invalide → 400
- ❌ `legal_entity_id` absent sur write → 400

---

## Tables minimales

1. **treasury_bank_account** (Comptes bancaires)
2. **treasury_bank_statement** (Relevés bancaires)
3. **treasury_transaction** (Transactions)
4. **treasury_reconciliation** (Rapprochements)
5. **treasury_cashbox** (Caisses)
6. **audit_log** (Append-only + hash chain)
7. **outbox_event** (Intégration événementielle)
8. **idempotency_key** (Anti double-paiement)
9. **coa_accounts** (Plan comptable)
10. **accounting_settings** (Paramétrages localisables)

---

## API Endpoints imposés

Base path: `/api/erp/treasury`

### Comptes bancaires
- `POST /bank-accounts` (Idempotency-Key requis)
- `GET /bank-accounts`
- `PATCH /bank-accounts/{id}`

### Relevés bancaires
- `POST /bank-statements/import` (Idempotency-Key requis)
- `GET /bank-statements?bank_account_id=...`

### Rapprochements
- `POST /reconciliations/run` (Idempotency-Key requis)
- `GET /reconciliations/{id}`

### Caisses
- `POST /cashboxes`
- `GET /cashboxes`

### Audit (read-only)
- `GET /audit/bank-accounts`
- `GET /audit/transactions?from=&to=`

---

## Events normalisés (Outbox pattern)

```json
{
  "event": "TREASURY.BANK_ACCOUNT.CREATED",
  "tenant_id": "uuid",
  "legal_entity_id": "uuid",
  "entity_id": "uuid",
  "timestamp": "ISO-8601",
  "correlation_id": "uuid"
}
```

**Règle**: Aucun event publié directement. Écriture dans `outbox_event`, worker publie.

---

## Tests obligatoires (bloquants)

### 1. Cross-tenant leak test
- Créer données tenant A
- Lire sous tenant B
- **Résultat attendu**: 0 résultat

### 2. Suspension test
- Token `billing_status=SUSPENDED`
- **Résultat attendu**: 403

### 3. Module disabled test
- Token sans `ERP_TREASURY_CORE` dans `enabled_modules`
- **Résultat attendu**: 403

### 4. Idempotency test
- POST avec même `Idempotency-Key` + même payload → même réponse
- POST avec même key + payload différent → 409

### 5. Immutability test
- Entrée `posted_at != null`
- Tentative update/delete
- **Résultat attendu**: 409 IMMUTABLE_POSTED

### 6. RLS verification
- Script SQL automatique vérifiant policies sur toutes tables

### 7. Forbidden DTO fields test
- POST avec `tenant_id` dans body
- **Résultat attendu**: 400 FORBIDDEN_FIELD

---

## Livrables exigés (5 obligatoires)

### 1. PR Spring fonctionnel
- Starter Platform intégré (George)
- TenantContextFilter + SaaSContractGuard
- RoutingDataSource hybride
- DbSessionInitializer (SET LOCAL)
- IdempotencyService
- AuditLogService (hash chain)
- OutboxService
- I18nClient
- Copilot endpoint

### 2. Migrations SQL complètes
- Tables + `tenant_id`/`legal_entity_id`/`country_code`
- RLS policies sur TOUTES tables
- Indexes de performance
- audit_log + outbox_event + idempotency_key
- Script vérification RLS (CI)

### 3. Manifest validé
- `manifest.json`
- `manifest.schema.json`
- Validation AJV en CI (gate)

### 4. CI green + artefacts
- Job `unit` ✅
- Job `integration` ✅
- Job `cross_tenant` ✅
- Job `migrations` ✅
- Job `secrets_scan` ✅
- Artefacts: reports + logs

### 5. Documentation contracts
- `TENANT_CONTRACT.md` (1 page max)
- `I18N_CONTRACT.md`
- `COPILOT_CONTRACT.md`
- `SECURITY_MODEL.md`
- `docs/MIGRATION_ROLLBACK.md`

---

## Definition of Done

Le microservice #1 est CONFORME si:

✅ Starter utilisé (pas de duplication classes Platform)
✅ Multi-tenant hybride opérationnel (3 modes)
✅ RLS prouvé par test cross-tenant (0 leak)
✅ Multi-company/country imposés
✅ i18n + copilot intégrés
✅ audit_log append-only + hash chain
✅ Idempotency sur endpoints critiques
✅ Outbox pattern appliqué
✅ Guard GitHub strict + CI green + preuves non vides
✅ Docs contracts présents
✅ Aucune classe Platform dupliquée localement

---

## Interdictions absolues

❌ Dupliquer classes du starter (TenantContextFilter, DbSessionInitializer, etc.)
❌ Accepter `tenant_id`/`legal_entity_id` dans les DTO clients
❌ Logique fiscale/OHADA/TVA dans ce CORE
❌ Intégration PSP directe (tout via Payment Gateway Waangu)
❌ Labels métier hardcodés (utiliser `*_i18n_key`)
❌ Publication directe events (utiliser outbox)
❌ Tables métier sans RLS
❌ Requête DB sans `SET LOCAL app.current_tenant`

---

## Règle de progression

**Tu ne passes pas au microservice #2 tant que ces 5 livrables ne sont pas complets avec preuves.**
