# Microservice #2 — asyst-ms-erp-comptabilite

**Module**: Comptabilité — Écritures / Journaux / Grand Livre
**Type**: CORE ERP
**Owner**: Ibrahim

---

## Classification

| Élément | Valeur |
|---------|--------|
| Nom | asyst-ms-erp-comptabilite |
| Module ID | `ERP_GL_CORE` |
| Type | CORE ERP |
| Owner | Ibrahim |
| Désactivable | ❌ NON |
| Facturable | ❌ NON (inclus CORE) |
| Dépend de | ERP Core uniquement |
| Supporte plugins | ✅ (OHADA, IFRS, TVA, Consolidation) |

---

## Objectif

Transformer le microservice d'écritures comptables pour qu'il soit:
1. **Multi-tenant hybride** (POOLED/SCHEMA/DEDICATED_DB)
2. **Audit-ready++** (immutabilité stricte, séquençage, verrou périodes)
3. **Extensible** par plugins (OHADA/IFRS consomment via events/API)
4. **ISA/ISQM conforme** (traçabilité totale)

---

## Périmètre fonctionnel STRICT

### Inclus (OBLIGATOIRE)
- Journaux comptables (définitions, types)
- Écritures comptables (header + lines)
- Séquençage automatique par tenant/legal_entity/year
- États: DRAFT → POSTED → REVERSED
- Verrouillage de périodes
- Exports techniques (grand livre, balance, journaux)

### Exclu (INTERDIT)
- Règles OHADA/IFRS/GAAP → PLUGINS
- TVA / Déclarations fiscales → PLUGINS
- Consolidation → PLUGIN
- États financiers légaux → PLUGINS

---

## Tables minimales

1. **gl_journal** (Journaux)
2. **gl_entry** (En-tête écriture)
3. **gl_entry_line** (Lignes écriture)
4. **gl_period_lock** (Verrouillage périodes)
5. **gl_sequence** (Numérotation atomique)
6. **audit_log** (Append-only + hash chain)
7. **outbox_event**
8. **idempotency_key**

---

## Particularités vs Microservice #1

### 1. Immutabilité STRICTE
- Écriture avec `status='POSTED'` → **IMMUTABLE**
- Correction uniquement via:
  - Écriture de contre-passation (REVERSAL)
  - Nouvelle écriture corrective
- **JAMAIS** de UPDATE/DELETE direct

### 2. Verrouillage de périodes
- `gl_period_lock(tenant_id, legal_entity_id, period, locked_at, locked_by)`
- Si période verrouillée → **REFUS** de toute nouvelle écriture sur cette période
- Déverrouillage réservé: `ERP_ADMIN` + audit obligatoire

### 3. Séquençage comptable
- Numérotation automatique: `{year}-{sequence}`
- Atomique via `gl_sequence` + `FOR UPDATE`
- Garantie: pas de trous (sauf reversal)

### 4. Partie double obligatoire
- Somme débits = Somme crédits
- Validation automatique avant POST
- Refus si déséquilibré

---

## API Endpoints imposés

Base path: `/api/erp/gl`

### Journaux
- `POST /journals` (Idempotency-Key)
- `GET /journals`
- `GET /journals/{id}`

### Écritures
- `POST /entries` (DRAFT, Idempotency-Key)
- `GET /entries`
- `GET /entries/{id}`
- `POST /entries/{id}/post` (DRAFT → POSTED, Idempotency-Key)
- `POST /entries/{id}/reverse` (create reversal entry, Idempotency-Key)

### Grand Livre / Balance
- `GET /ledger?from=&to=&account_id=`
- `GET /balance?period=`

### Périodes
- `POST /period-locks` (lock period, Idempotency-Key)
- `POST /period-locks/{id}/unlock` (admin only, audit)
- `GET /period-locks`

### Audit (read-only)
- `GET /audit/entries?from=&to=`
- `GET /audit/journals`
- `GET /audit/general-ledger`

---

## Guards spécifiques (en plus des guards standard)

### 1. Period Lock Guard
```java
private void assertNotLocked(LocalDate date) {
    String period = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
    Integer count = jdbc.queryForObject("""
        SELECT count(*) FROM gl_period_lock 
        WHERE period = ?
    """, Integer.class, period);
    
    if (count != null && count > 0) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "PERIOD_LOCKED");
    }
}
```

### 2. Immutability Guard
```java
private void assertMutable(String status, Instant postedAt) {
    if ("POSTED".equals(status) || postedAt != null) {
        throw new ResponseStatusException(HttpStatus.CONFLICT, "IMMUTABLE_POSTED");
    }
}
```

### 3. Balance Validation
```java
private void validateBalance(List<EntryLine> lines) {
    BigDecimal sumDebit = lines.stream()
        .map(EntryLine::debit)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    
    BigDecimal sumCredit = lines.stream()
        .map(EntryLine::credit)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    
    if (sumDebit.compareTo(sumCredit) != 0) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "UNBALANCED_ENTRY");
    }
}
```

---

## Tests obligatoires supplémentaires

### 1. Immutability test
- Create entry → post → attempt update → **409**
- Create entry → post → attempt delete → **409**

### 2. Period lock test
- Lock period "2026-01"
- Attempt to post entry dated 2026-01-15 → **409 PERIOD_LOCKED**

### 3. Balance validation test
- Create unbalanced entry (debit ≠ credit) → **400 UNBALANCED_ENTRY**

### 4. Sequence test
- Create multiple entries in parallel (same tenant/LE/year)
- Verify unique sequential numbers
- No gaps (except reversal)

### 5. Reversal test
- Post entry → reverse → verify:
  - Original entry unchanged
  - New reversal entry created
  - Both linked via `reversal_ref`

---

## Livrables (identiques au #1 + spécificités)

### 1. PR Spring
- Même starter Platform
- Services: JournalService, EntryService, PeriodLockService
- Guards: period lock + immutability + balance validation
- Séquençage atomique (gl_sequence)

### 2. Migrations SQL
- Tables + RLS
- Trigger immutabilité (optionnel, guard service suffit)
- Indexes performance

### 3. Manifest
- `ERP_GL_CORE`
- Capabilities: mêmes que #1

### 4. CI green
- unit/integration/cross_tenant/migrations/secrets_scan
- Tests supplémentaires: immutability, period_lock, balance, sequence

### 5. Docs
- TENANT_CONTRACT.md
- I18N_CONTRACT.md
- COPILOT_CONTRACT.md
- SECURITY_MODEL.md
- MIGRATION_ROLLBACK.md

---

## Definition of Done

Le microservice #2 est CONFORME si:

✅ Tous les critères du microservice #1
✅ Immutabilité POSTED prouvée (tests)
✅ Period lock opérationnel + tests
✅ Séquençage atomique + tests parallèles
✅ Balance validation (debit=credit) + tests
✅ Reversal mechanism + tests
✅ Aucune modification directe d'écriture postée (409)
