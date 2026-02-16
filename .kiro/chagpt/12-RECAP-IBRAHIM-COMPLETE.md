# 📘 RÉCAPITULATIF COMPLET — Cahier des Charges Ibrahim

**Date**: 2026-02-07  
**Version**: FINALE IMPOSABLE  
**Niveau**: GAFA x10 / Big-4 / Régulateurs / FinTech

---

## 🎯 Objectif Global

Transformer les microservices ERP backend existants en modules SaaS multi-tenant hybrides conformes aux exigences:
- **Multi-tenant** (POOLED / SCHEMA / DEDICATED_DB)
- **Multi-company** (legal_entity_id obligatoire)
- **Multi-country** (country_code + localisation)
- **Multi-language** (i18n automatique)
- **Copilot-ready** (intents déclarés)
- **Audit-ready** (ISA/ISQM, immutabilité, hash chain)
- **FinTech-grade** (idempotency, outbox, RLS prouvé)

---

## 📦 Microservices Assignés à Ibrahim

### ✅ Microservice #1: erp-ms-tresorerie-backend
- **Module**: Comptabilité générale / Trésorerie
- **Type**: CORE ERP (NON OPTIONNEL)
- **Contenu**: Plan comptable, journaux, banques, caisses, rapprochements
- **Fichiers livrés**:
  - `04-IBRAHIM-MS1-TRESORERIE-SPECS.md`
  - `05-IBRAHIM-MS1-CODE-SPRING.md`
  - `06-IBRAHIM-MS1-SQL-MIGRATIONS.md`
  - `07-IBRAHIM-MS1-GITHUB-CI.md`
  - `08-IBRAHIM-MS1-CONTRACTS-DOCS.md`

### ✅ Microservice #2: asyst-ms-erp-comptabilite
- **Module**: Comptabilité — Écritures / Grand Livre
- **Type**: CORE ERP (NON OPTIONNEL)
- **Particularités**: Immutabilité stricte, séquençage, verrouillage périodes
- **Fichiers livrés**:
  - `09-IBRAHIM-MS2-COMPTABILITE-SPECS.md`

### ✅ Microservice #3: erp-ms-comptabilite-analytic
- **Module**: Comptabilité Analytique
- **Type**: CORE ERP (NON OPTIONNEL)
- **Contenu**: Axes, dimensions, allocations multi-axes
- **Fichiers livrés**:
  - `10-IBRAHIM-MS3-ANALYTIC-SPECS.md`

### 🔄 Microservices Restants (même ossature)
4. waangu-gestion-commerciale-produit
5. waangu-gestion-commerciale-stock
6. waangu-gestion-commerciale-inventaire
7. waangu-gestion-commerciale-parametage

**Règle**: Copier-coller l'ossature des microservices #1, #2, #3 (tables + RLS + manifest + CI + contracts).

---

## 🏗️ Architecture Imposée

### Stack Technique
- **Backend**: Spring Boot 3.x + Java 21
- **Database**: PostgreSQL 16
- **Migration**: Flyway
- **Data Access**: JdbcTemplate (contrôle RLS)
- **Auth**: Keycloak OIDC (JWT)
- **CI/CD**: GitHub Actions
- **Testing**: JUnit 5 + Testcontainers

### Dépendances Obligatoires
```xml
<dependency>
    <groupId>com.waangu.platform</groupId>
    <artifactId>waangu-saas-spring-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Interdit**: Réimplémenter localement les classes suivantes (viennent du starter):
- TenantContextFilter
- TenantRegistryClient
- RoutingDataSource
- DbSessionInitializer
- SaaSContractGuard
- ForbiddenBodyFieldsFilter
- IdempotencyService
- AuditLogService
- OutboxService
- I18nClient
- CopilotIntentController

---

## 📋 Checklist Universelle (20 Points — OBLIGATOIRE)

### Multi-Tenant (4 points)
1. ✅ `tenant_id` dans toutes tables métier
2. ✅ `legal_entity_id` dans tables financières
3. ✅ RLS activé + policies sur toutes tables
4. ✅ Test cross-tenant (0 leak)

### Multi-Country / Company / Language (4 points)
5. ✅ `country_code` pour localisation
6. ✅ `@FinancialEndpoint` pour legal_entity_id
7. ✅ i18n (`*_i18n_key` + `*_source`)
8. ✅ Integration Translation Service

### Platform Starter (4 points)
9. ✅ Starter dépendance ajoutée
10. ✅ TenantContextFilter utilisé
11. ✅ DbSessionInitializer utilisé
12. ✅ RoutingDataSource utilisé

### Copilot (2 points)
13. ✅ `/copilot/intents` exposé
14. ✅ RBAC déclaré par intent

### Audit & Immutabilité (3 points)
15. ✅ audit_log pour mutations
16. ✅ Hash chain (prev_hash/curr_hash)
17. ✅ Guard immutabilité (409 si posted)

### Idempotency & Outbox (2 points)
18. ✅ Idempotency-Key sur POST critiques
19. ✅ Outbox pattern (pas d'appels directs)

### CI & Manifest (1 point)
20. ✅ Manifest AJV validé + CI green

---

## 🧪 Tests Obligatoires (Gates CI)

### Jobs CI Minimaux (4 obligatoires + 2 recommandés)
1. **unit** ✅ (obligatoire)
2. **integration** ✅ (obligatoire)
3. **cross_tenant** ✅ (obligatoire, bloquant)
4. **migrations** ✅ (obligatoire, inclut AJV + RLS check)
5. **secrets_scan** ✅ (recommandé, gitleaks)
6. **perf_test** ⚪ (optionnel, continue-on-error)

### Tests Spécifiques par Type
#### Tous microservices:
- Cross-tenant leak (Tenant A vs Tenant B = 0)
- Module disabled (403)
- Billing suspended (403)
- Idempotency (same key = same response)
- Forbidden DTO fields (tenant_id dans body = 400)

#### Microservice #1 (Trésorerie):
- Standard tests ci-dessus

#### Microservice #2 (Comptabilité):
- **+ Immutability** (posted entry = 409 on update)
- **+ Period lock** (locked period = 409 on post)
- **+ Balance validation** (debit ≠ credit = 400)
- **+ Sequence** (no gaps, atomic)
- **+ Reversal** (original unchanged, new entry created)

#### Microservice #3 (Analytic):
- **+ Posted rule immutability** (posted rule = 409)
- **+ Allocation idempotency** (critical)
- **+ Performance** (10k records < threshold)

---

## 📄 Livrables par Microservice (5 obligatoires)

### 1. PR Spring Fonctionnel
- Controllers (REST endpoints)
- Services (business logic + guards)
- Repositories (JdbcTemplate)
- DTOs (request/response, NO tenant_id)
- Integration starter Platform

### 2. Migrations SQL Complètes
- `V1__*_tables.sql` (tables métier + i18n + audit fields)
- `V2__audit_outbox_idempotency.sql` (tables système)
- `V3__rls.sql` (policies sur TOUTES tables)
- `V4__indexes_constraints.sql` (performance + intégrité)

### 3. Manifest Validé
- `manifest.json` (déclaration module)
- `manifest.schema.json` (JSON schema validation)
- Validation AJV en CI (gate)

### 4. CI Green + Artefacts
- Tous jobs obligatoires passent (unit/integration/cross_tenant/migrations)
- Artefacts uploadés (reports, logs, manifest)
- Guard strict passé (checklist + preuves + SQL plan)

### 5. Documentation Contracts (5 fichiers)
- `TENANT_CONTRACT.md` (JWT claims + hard-fail rules)
- `I18N_CONTRACT.md` (Translation Service integration)
- `COPILOT_CONTRACT.md` (intents + RBAC)
- `SECURITY_MODEL.md` (RLS + audit + idempotency + outbox)
- `docs/MIGRATION_ROLLBACK.md` (stratégie migration)

---

## 🚨 Interdictions Absolues

❌ **JAMAIS**:
1. Dupliquer classes du starter Platform
2. Accepter `tenant_id`/`legal_entity_id` dans DTO request body
3. Mettre logique fiscale/OHADA/IFRS dans CORE (→ plugins)
4. Appeler directement PSP externes (→ Payment Gateway Waangu)
5. Hardcoder labels métier (→ i18n keys)
6. Publier events directement (→ outbox pattern)
7. Tables métier sans RLS
8. Requête DB sans `SET LOCAL app.current_tenant`
9. Modifier écritures `posted_at != null` (→ immutabilité)
10. Commiter secrets (→ gitleaks scan)

---

## 🔒 Guards Obligatoires (Code Examples)

### 1. Tenant Context Guard (dans TenantContextFilter du starter)
```java
if (tenantId == null || billingStatus == null) {
    throw new ResponseStatusException(401, "MISSING_TENANT_CONTEXT");
}
if (!List.of("ACTIVE", "TRIAL").contains(billingStatus)) {
    throw new ResponseStatusException(403, "TENANT_SUSPENDED");
}
if (!enabledModules.contains(requiredModule)) {
    throw new ResponseStatusException(403, "MODULE_DISABLED");
}
```

### 2. Legal Entity Guard (@FinancialEndpoint)
```java
@Around("@within(FinancialEndpoint) || @annotation(FinancialEndpoint)")
public Object enforce(ProceedingJoinPoint pjp) {
    if (TenantContextHolder.get().legalEntityId() == null) {
        throw new ResponseStatusException(400, "LEGAL_ENTITY_REQUIRED");
    }
    return pjp.proceed();
}
```

### 3. Forbidden Body Fields Filter
```java
List<String> forbidden = List.of("tenant_id", "legal_entity_id", "country_code");
for (String field : forbidden) {
    if (bodyJson.has(field)) {
        throw new ResponseStatusException(400, "FORBIDDEN_FIELD: " + field);
    }
}
```

### 4. Immutability Guard (Microservice #2, #3)
```java
if (entity.postedAt() != null) {
    throw new ResponseStatusException(409, "IMMUTABLE_POSTED");
}
```

### 5. Period Lock Guard (Microservice #2)
```java
String period = date.getYear() + "-" + String.format("%02d", date.getMonthValue());
if (jdbc.queryForObject("SELECT count(*) FROM gl_period_lock WHERE period=?", Integer.class, period) > 0) {
    throw new ResponseStatusException(409, "PERIOD_LOCKED");
}
```

---

## 🛠️ Commandes Utiles

### Build & Test Local
```bash
./mvnw clean install
./mvnw test
./mvnw -Dtest='*CrossTenantTest' test
```

### CI Local Simulation
```bash
# Manifest validation
npm i ajv
node -e "const Ajv=require('ajv');const fs=require('fs');const ajv=new Ajv(); \
const schema=JSON.parse(fs.readFileSync('manifest.schema.json')); \
const data=JSON.parse(fs.readFileSync('manifest.json')); \
const validate=ajv.compile(schema); \
if(!validate(data)){console.error(validate.errors);process.exit(1);}"

# Flyway migrate (Testcontainers)
docker run --rm -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16
./mvnw flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/erp_test

# Secrets scan
docker run --rm -v $(pwd):/repo zricethezav/gitleaks:latest detect --source /repo
```

---

## 📊 Métriques de Conformité

| Critère | Microservice #1 | Microservice #2 | Microservice #3 |
|---------|----------------|----------------|----------------|
| Multi-tenant (RLS) | ✅ | ✅ | ✅ |
| Multi-company | ✅ | ✅ | ✅ |
| Multi-country | ✅ | ✅ | ✅ |
| Multi-language (i18n) | ✅ | ✅ | ✅ |
| Copilot intents | ✅ | ✅ | ✅ |
| Audit log (hash chain) | ✅ | ✅ | ✅ |
| Idempotency | ✅ | ✅ | ✅ |
| Outbox pattern | ✅ | ✅ | ✅ |
| Immutabilité | ⚪ | ✅ | ✅ (rules) |
| Period lock | N/A | ✅ | N/A |
| Séquençage | N/A | ✅ | N/A |

---

## ✅ Definition of Done (Critère Final)

Un microservice d'Ibrahim est **CONFORME** si et seulement si:

1. ✅ Checklist 20/20 cochée + preuves CI non vides
2. ✅ Tous jobs CI obligatoires GREEN (unit/integration/cross_tenant/migrations)
3. ✅ Cross-tenant test = 0 leak (prouvé)
4. ✅ Manifest validé par AJV
5. ✅ RLS policies présentes sur TOUTES tables tenant-scopées
6. ✅ Aucune classe Platform dupliquée localement
7. ✅ 5 docs contracts présents et remplis
8. ✅ GitHub guard strict PASS (waangu_pr_guard_strict)
9. ✅ Migration/Rollback plan rempli si SQL change
10. ✅ Review approuvée (CODEOWNERS: Platform + QA + DevOps)

**Si un seul critère manque → REFUS MERGE.**

---

## 🎓 Règle de Progression

**Ordre strict**:
1. Implémenter Microservice #1 avec les 5 livrables ✅
2. Valider conformité #1 (DoD ci-dessus) ✅
3. Copier-coller ossature sur #2 ✅
4. Implémenter spécificités #2 (immutabilité, period lock, etc.) ✅
5. Valider conformité #2 ✅
6. Copier-coller ossature sur #3 ✅
7. Implémenter spécificités #3 (axes analytiques, allocations) ✅
8. Valider conformité #3 ✅
9. Répéter pour microservices 4-7 (même méthode)

**Interdiction**: Passer au suivant avant validation complète du précédent.

---

## 📞 Contacts & Escalation

| Rôle | Nom | Responsabilité |
|------|-----|----------------|
| **Backend Platform Lead** | George | Starter, TenantContext, Routing |
| **SDK Engineer** | Jackson | Manifest validation, SDK clients |
| **QA Automation** | Didier | Tests, preuves CI |
| **DevOps** | Hugues | CI/CD, secrets, déploiement |
| **Network Admin** | Frantz | Politiques réseau, segmentation |
| **Translation Service** | George + Landry | i18n integration |
| **Copilot** | César | Intents, hybrid AI |
| **CTO** | Trésor | Arbitrages CORE/PLUGIN, validation finale |

**Escalation**: Ibrahim → Didier (QA) → George (Platform) → Trésor (CTO)

---

## 🏁 Conclusion

Ce cahier des charges constitue **la norme absolue** pour la transformation SaaS multi-tenant des microservices ERP backend.

Aucune exception, aucune déviation n'est autorisée sans validation écrite du CTO.

Tout microservice livré selon ces spécifications est **garanti conforme** aux exigences:
- Régulateurs (GAFA x10)
- Auditeurs Big-4 (ISA/ISQM)
- FinTech-grade (immutabilité, idempotency, audit trail)
- SaaS Catalog Waangu 360 (multi-tenant hybride)

**Ibrahim, tu as maintenant tous les éléments pour réussir. Bon courage!**
