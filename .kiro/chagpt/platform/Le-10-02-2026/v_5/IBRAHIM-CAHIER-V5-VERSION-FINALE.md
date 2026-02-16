# Cahier des charges Ibrahim — Version finale (V5)

**Référence officielle** : WAANGU_ERP_WITH_RESTO_LE_10_02_2026-V5.xlsx  
**Destinataire** : Ibrahim — Backend ERP Spring Boot  
**Statut** : Norme obligatoire ; aucun microservice en production sans les 10 garanties.

---

# 🎯 IBRAHIM — Ton périmètre officiel (V5)

Après alignement avec la version V5, les microservices dédiés à **toi** sont les suivants.

---

## 1️⃣ erp-ms-tresorerie-backend

**Domaine :**
- Gestion comptes de trésorerie
- Flux financiers
- Mouvements
- Intégration Engagement Hub
- Rapprochements futurs

**Criticité :**
- 🔥 FinTech critique
- 🔥 Exposé aux paiements
- 🔥 Double-spend impossible
- 🔥 Idempotency obligatoire
- 🔥 Isolation multi-sociétés stricte

---

## 2️⃣ asyst-ms-erp-comptabilite

**Domaine :**
- Journaux
- Écritures comptables
- Séquences
- Verrouillage périodes
- Immutabilité
- Export audit

**Criticité :**
- 🔥 Audit OPC
- 🔥 Normes ISA / IFRS / IPSAS ready
- 🔥 Hash chain obligatoire
- 🔥 Period lock irréversible
- 🔥 Isolation légale absolue

---

## 3️⃣ erp-ms-comptabilite-analytic

**Domaine :**
- Axes analytiques
- Dimensions
- Règles d’allocation
- Exécution allocations
- Performance volumétrique

**Criticité :**
- 🔥 Cross-tenant isolation
- 🔥 Allocation financière cohérente
- 🔥 Performance 10k+ lignes
- 🔥 Idempotency sur run allocation

---

# ❗ Ce que TU ne développes PAS

Ibrahim, tu **ne développes pas** :

- Catalogue SaaS  
- Facturation SaaS  
- Onboarding tenant  
- Traduction automatique  
- Copilote IA  
- Payment Gateway engine interne  

Tu **dois t’intégrer proprement** à ces services (API, contrats, clés i18n, intents, idempotency côté gateway).

---

# ⚠️ Ce que TU dois garantir systématiquement

Pour **chacun** des 3 microservices :

1. **Multi-tenant hybride** (mutualisé / schéma / base dédiée)  
2. **Multi-sociétés** (legal_entity_id, guards, RLS)  
3. **Multi-pays** (country_code, paramètres localisables)  
4. **Multi-langues** (i18n ready, clés + API traduction)  
5. **Isolation RLS stricte** (policies sur toutes les tables, preuve cross-tenant)  
6. **Immutabilité financière** (écritures postées non modifiables ; contre-passation uniquement)  
7. **Idempotency** (clé sur opérations critiques ; pas de double traitement)  
8. **Outbox pattern** (événements vers Engagement Hub / autres via outbox, pas d’appel direct dans la transaction)  
9. **Audit trail hash chain** (audit_log append-only, prev_hash / curr_hash)  
10. **CI hard guard** (pipeline vert, jobs obligatoires, blocage merge si non conforme)  

**Aucun microservice ne doit passer en production sans ces 10 éléments.**

---

# 🧱 Structure attendue dans chacun de tes repos

## Arborescence obligatoire

```
src/main/java/<package_base>/
  ├── config/
  │    ├── SecurityConfig.java
  │    ├── TenantContextFilter.java
  │    ├── RoutingDataSource.java
  │    └── OpenTelemetryConfig.java
  ├── controller/
  ├── service/
  ├── repository/
  ├── domain/
  ├── guard/
  ├── audit/
  ├── outbox/
  └── idempotency/

db/migration/
  ├── V1__init.sql
  ├── V2__rls.sql
  ├── V3__audit.sql
  └── V4__idempotency.sql

.github/workflows/
  └── ci.yml

TENANT_CONTRACT.md
MIGRATION_ROLLBACK.md
manifest.json
manifest.schema.json
```

## Script de vérification de la structure (garde-fou)

Enregistre ce script à la racine du repo (ex. `scripts/check_repo_structure.sh`) et exécute-le en CI ou en local.

```bash
#!/bin/bash
# check_repo_structure.sh — Garde-fou structure repo Ibrahim (V5)
set -e
ROOT="${1:-.}"

fail() { echo "❌ $1"; exit 1; }
ok()   { echo "  ✅ $1"; }

echo "Checking repo structure (V5)..."

# Dossiers Java obligatoires
for dir in config controller service repository domain guard audit outbox idempotency; do
  [ -d "$ROOT/src/main/java" ] || fail "src/main/java missing"
  # On vérifie qu'au moins un des packages contient ce dossier (selon votre convention)
  if ! find "$ROOT/src/main/java" -type d -name "$dir" 2>/dev/null | head -1 | grep -q .; then
    fail "Package '$dir' not found under src/main/java"
  fi
  ok "package $dir"
done

# Fichiers config critiques (ou équivalent depuis starter)
for f in SecurityConfig.java TenantContextFilter.java RoutingDataSource.java; do
  if ! find "$ROOT/src/main/java" -name "$f" 2>/dev/null | grep -q .; then
    echo "  ⚠️ $f not found (may be in platform starter)"
  fi
done

# Migrations
for m in V1__init.sql V2__rls.sql V3__audit.sql V4__idempotency.sql; do
  [ -f "$ROOT/db/migration/$m" ] || fail "db/migration/$m missing"
  ok "db/migration/$m"
done

# CI
[ -f "$ROOT/.github/workflows/ci.yml" ] || fail ".github/workflows/ci.yml missing"
ok "ci.yml"

# Docs & manifest
[ -f "$ROOT/TENANT_CONTRACT.md" ] || fail "TENANT_CONTRACT.md missing"
[ -f "$ROOT/MIGRATION_ROLLBACK.md" ] || fail "MIGRATION_ROLLBACK.md missing"
[ -f "$ROOT/manifest.json" ] || fail "manifest.json missing"
[ -f "$ROOT/manifest.schema.json" ] || fail "manifest.schema.json missing"
ok "TENANT_CONTRACT.md, MIGRATION_ROLLBACK.md, manifest.json, manifest.schema.json"

echo ""
echo "✅ Repo structure (V5) OK"
```

## Exemple de contenu minimal (référence)

### config/TenantContextFilter.java (extrait)

Tu peux utiliser le **starter plateforme** (recommandé) ou, si tu dois l’avoir localement, respecter au minimum :

- Lecture des claims JWT : `tenant_id`, `legal_entity_id`, `billing_status`, `enabled_modules`, `country_code`, `locale`.
- Refus 401 si `tenant_id` absent.
- Refus 403 si `billing_status` != ACTIVE/TRIAL ou si le module n’est pas dans `enabled_modules`.
- Mise en contexte (TenantContextHolder) pour toute la requête.

### db/migration/V2__rls.sql (extrait)

```sql
-- Exemple : une table métier
ALTER TABLE treasury_account ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_treasury_account ON treasury_account
USING (
  tenant_id = (current_setting('app.current_tenant'))::uuid
  AND legal_entity_id = (current_setting('app.current_legal_entity'))::uuid
);
```

### db/migration/V3__audit.sql (extrait)

```sql
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
  prev_hash TEXT,
  curr_hash TEXT
);

ALTER TABLE audit_log ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_audit_log ON audit_log
USING (tenant_id = (current_setting('app.current_tenant'))::uuid);
```

### db/migration/V4__idempotency.sql (extrait)

```sql
CREATE TABLE idempotency_key (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  legal_entity_id UUID NOT NULL,
  key TEXT NOT NULL,
  request_hash TEXT NOT NULL,
  response JSONB,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX uq_idempotency ON idempotency_key(tenant_id, legal_entity_id, key);
ALTER TABLE idempotency_key ENABLE ROW LEVEL SECURITY;
CREATE POLICY rls_idempotency ON idempotency_key
USING (tenant_id = (current_setting('app.current_tenant'))::uuid);
```

---

# 🔒 Rappel ferme

Ibrahim,

Tes microservices sont le **cœur financier** de Waangu 360.

La moindre faille :

- fuite cross-tenant  
- écriture modifiable après post  
- absence de lock période  
- double spend  
- race condition  
- RLS mal configuré  
- billing non vérifié  
- idempotency absente  

➡ **met en danger :**

- conformité nationale  
- audits OPC  
- crédibilité fintech  
- certifications ISO/SOC2  
- confiance investisseurs  

---

# Garde-fous de clôture (par microservice)

Avant de considérer un MS comme prêt pour la production, vérifier :

- [ ] **GF-1** Multi-tenant hybride opérationnel (3 modes).  
- [ ] **GF-2** Multi-sociétés (legal_entity_id partout où il faut + RLS).  
- [ ] **GF-3** Multi-pays (country_code + paramètres).  
- [ ] **GF-4** i18n ready (clés, pas de libellés en dur métier).  
- [ ] **GF-5** RLS sur toutes les tables métier + test cross-tenant = 0 leak.  
- [ ] **GF-6** Immutabilité respectée (MS#2, #3 ; MS#1 selon règles métier).  
- [ ] **GF-7** Idempotency sur opérations critiques (table + header + test).  
- [ ] **GF-8** Outbox utilisé pour événements externes (Engagement Hub, etc.).  
- [ ] **GF-9** Audit trail avec hash chain (prev_hash / curr_hash).  
- [ ] **GF-10** CI green + hard guard (checklist, jobs obligatoires, pas de merge si rouge).  

---

# 🎯 Prochaine étape

Maintenant que ton périmètre est clarifié :

1. **Finaliser l’ossature commune obligatoire** (structure repo, migrations de base, CI, contrats, manifest).  
2. **Reprendre microservice par microservice.**  
3. **Commencer par : erp-ms-tresorerie-backend.**  
4. **Implémentation complète production-grade** (APIs, RLS, idempotency, audit, outbox, tests, CI).

Choix immédiat :

- **Option A** : On attaque **erp-ms-tresorerie-backend** maintenant (détail des endpoints, tables, guards, tests).  
- **Option B** : Je te formalise d’abord un **tableau de conformité croisé V5 ↔ responsabilités Ibrahim** (Partie 5 détaillée).

---

# Tableau de conformité croisé V5 ↔ Ibrahim (résumé)

| Garantie | MS#1 Trésorerie | MS#2 Compta | MS#3 Analytic |
|----------|------------------|-------------|----------------|
| 1. Multi-tenant hybride | ✔ | ✔ | ✔ |
| 2. Multi-sociétés | ✔ | ✔ | ✔ |
| 3. Multi-pays | ✔ | ✔ | ✔ |
| 4. Multi-langues (i18n) | ✔ | ✔ | ✔ |
| 5. RLS stricte | ✔ | ✔ | ✔ |
| 6. Immutabilité financière | selon métier | ✔ | ✔ (règles) |
| 7. Idempotency | ✔ | ✔ | ✔ |
| 8. Outbox pattern | ✔ | ✔ | ✔ |
| 9. Audit trail hash chain | ✔ | ✔ | ✔ |
| 10. CI hard guard | ✔ | ✔ | ✔ |

Ce document est la **version finale** du cadre qui te est dédié (V5). Toute déviation doit être documentée et validée.
