# Table des matières détaillée — Cahier des charges Ibrahim (ERP Backend)

**Waangu 360 — SaaS multi-tenant hybride, multi-langues, multi-sociétés, FinTech**  
**Référence**: Tableau révisé microservices/micro-frontends et répartition des tâches (10-02-2026)  
**Règle**: Un seul chapitre à la fois ; le suivant n’est abordé qu’une fois le précédent satisfaisant.

---

## Microservices sous la responsabilité d’Ibrahim (Backend uniquement)

| # | Microservice | Rôle métier | Stack | Type |
|---|--------------|-------------|-------|------|
| 1 | **erp-ms-tresorerie-backend** | Trésorerie, banques/caisses, rapprochements, flux financiers ERP | Spring Boot 3, PostgreSQL, RLS | CORE |
| 2 | **asyst-ms-erp-comptabilite** | Comptabilité générale, journaux, écritures, verrouillage périodes, préparation audit OPC | id. | CORE |
| 3 | **erp-ms-comptabilite-analytic** | Axes analytiques, dimensions, règles d’allocation, postings analytiques | id. | CORE |

**Hors périmètre Ibrahim**: micro-frontends (Cedric/Giscard), moteur traduction (George/Landry), moteur copilote (César), logique métier Engagement Hub / Payment Gateway (intégration API uniquement).

---

## Intégrations plateforme (branchement contractuel)

- **Traduction automatique**: équipe dédiée ; Ibrahim génère i18n keys et consomme l’API (n’implémente pas le service).
- **Copilote hybride**: équipe dédiée ; Ibrahim expose intents métier, RBAC et audit.
- **Engagement Hub** et **Payment Gateway**: acquis ; branchement via API (idempotency, statuts, callbacks), aucune logique PCI ni stockage sensible côté Ibrahim.

---

## Structure du cahier (ordre de rédaction et validation)

---

### PARTIE 0 — Cadre général *(à valider en premier)*

| § | Titre | Contenu principal | Garde-fou / livrable |
|---|--------|-------------------|------------------------|
| **0.1** | Objet du cahier des charges | Transformation MS/MFE en multi-tenant hybride, multi-langues, multi-sociétés ; vision Waangu 360 FinTech ; normes internationales et nationales | Document unique objet + périmètre |
| **0.2** | Périmètre exact d’Ibrahim | 3 microservices backend listés ; frontend, traduction, copilote, gateway en intégration uniquement | Liste officielle MS + hors périmètre |
| **0.3** | Résultats attendus | ERP SaaS multi-tenant hybride ; catalogue activable/désactivable ; audit-ready ; 3 modes (mutualisé / schéma / base dédiée) | Definition of Done Partie 0 |
| **0.4** | Références réglementaires | FinTech, audit (ISA/ISQM/OPC), données (RGPD, localisation), certifications (ISO 27001, SOC2) | Référentiel normes + mapping court |

**Chapitre 0 version finale**: détails complets, codes/scripts et garde-fous pour chaque section (voir `CHAPITRE-00-VERSION-FINALE.md`).

---

### PARTIE 1 — Stack technique imposée (ERP Backend)

| § | Titre | Contenu principal | Garde-fou / livrable |
|---|--------|-------------------|------------------------|
| **1.1** | Technologies obligatoires | Java 21, Spring Boot 3.x, Spring Security + Keycloak, JdbcTemplate (JPA interdit par défaut), PostgreSQL + RLS, Flyway, GitHub Actions | Liste versionnée + critère d’acceptation |
| **1.2** | Contraintes induites par React Native Expo | REST strict, pagination serveur, i18n par clé, erreurs normalisées | Contrat API (extrait) |

---

### PARTIE 2 — Modèle multi-tenant hybride Waangu

| § | Titre | Contenu principal | Garde-fou / livrable |
|---|--------|-------------------|------------------------|
| **2.1** | Hiérarchie Pays → Tenant → Legal Entity → Modules | Modèle de données et règles de portée | Schéma + règles d’usage |
| **2.2** | Modes: Mutualisé / Schéma / Base dédiée | Description et critères de choix | Tableau décisionnel |
| **2.3** | Règles absolues | tenant_id, legal_entity_id, country_code obligatoires | Checklist champs obligatoires |
| **2.4** | Isolation par RLS PostgreSQL | Politiques, SET LOCAL, preuve d’isolation | Script vérification RLS + test cross-tenant |

---

### PARTIE 3 — Contrats d’intégration plateforme

| § | Titre | Contenu principal | Garde-fou / livrable |
|---|--------|-------------------|------------------------|
| **3.1** | Contrat Tenant & Sécurité | Claims Keycloak, hard-fail, session DB | TENANT_CONTRACT.md (référence) |
| **3.2** | Contrat Traduction automatique | i18n keys, API traduction (consommation) | I18N_CONTRACT.md (référence) |
| **3.3** | Contrat Copilote hybride | Intents, RBAC, audit | COPILOT_CONTRACT.md (référence) |
| **3.4** | Contrat Engagement Hub & Payment Gateway | API only, idempotency, callbacks, pas de PCI | Contrat court + liste interdictions |

---

### PARTIE 4 — Starter ERP Backend Spring (obligatoire)

| § | Titre | Contenu principal | Garde-fou / livrable |
|---|--------|-------------------|------------------------|
| **4.1** | Composants imposés | TenantContextFilter, RoutingDataSource, TenantRegistryClient, DbSessionInitializer, SaaSContractGuard, IdempotencyService, AuditLogService, OutboxService, Forbidden DTO Fields Guard | Liste composants + responsabilité George |
| **4.2** | Interdictions formelles | Pas de duplication des classes starter ; pas de contournement RLS | Checklist CI “no duplication” |

---

### PARTIE 5 — Sécurité, audit & conformité FinTech

| § | Titre | Contenu principal | Garde-fou / livrable |
|---|--------|-------------------|------------------------|
| **5.1** | Sécurité des données | Chiffrement, accès, secrets | SECURITY_MODEL.md (référence) |
| **5.2** | Audit trail immuable | OPC ready, hash chain, preuves | Règles d’écriture audit_log |
| **5.3** | Conservation & localisation | DC, AWS, duplication, data residency | Règles par environnement |

---

### PARTIE 6 — CI/CD & qualité (GitHub)

| § | Titre | Contenu principal | Garde-fou / livrable |
|---|--------|-------------------|------------------------|
| **6.1** | Environnements | dev / test / preprod / prod | Matrice env × usage |
| **6.2** | Jobs CI obligatoires | unit, integration, cross_tenant, migrations, secrets_scan | Noms jobs + statut requis |
| **6.3** | Guards GitHub | PR template, checklist 20 points, blocages (preuves, migration/rollback si SQL) | Workflow waangu_pr_guard_strict |

---

### PARTIE 7 — Microservices ERP d’Ibrahim (chapitres détaillés)

| Chapitre | Microservice | Contenu principal | Garde-fou / livrable |
|----------|--------------|-------------------|------------------------|
| **7.1** | erp-ms-tresorerie-backend | Rôle métier, modèle de données, APIs, guards, audit, tests, manifest | 5 livrables (Spring, SQL, manifest, CI, docs) |
| **7.2** | asyst-ms-erp-comptabilite | Écritures, journaux, verrouillage périodes, audit OPC, tests renforcés | Id. + immutabilité + period lock |
| **7.3** | erp-ms-comptabilite-analytic | Axes, dimensions, allocations, performance, tests volumétriques | Id. + idempotency allocations + perf |

**Règle**: Chapitre 7.k clos uniquement si 5 livrables livrés et DoD du chapitre respectée.

---

### PARTIE 8 — Matrice de conformité

| Critère | Exigence | Vérification |
|---------|----------|--------------|
| Multi-tenant | tenant_id + RLS + 3 modes | Tests + CI |
| Multi-sociétés | legal_entity_id sur données financières | Guards + RLS |
| Multi-pays | country_code + paramètres localisables | Modèle + APIs |
| Multi-langues | i18n keys + traduction (API) | I18N_CONTRACT |
| Audit OPC | Trail immuable, preuves, OPC ready | SECURITY_MODEL + tests |
| FinTech | Idempotency, outbox, immutabilité | Contrats + tests |

---

### PARTIE 9 — Annexes obligatoires

- TENANT_CONTRACT.md  
- I18N_CONTRACT.md  
- COPILOT_CONTRACT.md  
- SECURITY_MODEL.md  
- MIGRATION_ROLLBACK.md  

Chaque annexe est référencée dans les parties concernées et doit exister à la clôture du cahier.

---

## Ordre de progression imposé

1. **Valider Partie 0** (Chapitre 0 version finale) → objet, périmètre, résultats, références.
2. **Compléter / clôturer** les chapitres déjà rédigés si des compléments indispensables (nouvelles données) sont identifiés.
3. **Enchaîner** Partie 1 → 2 → 3 → 4 → 5 → 6 → 7.1 → 7.2 → 7.3 → 8 → 9.
4. **Ne pas passer au chapitre suivant** tant que le précédent n’est pas satisfaisant (DoD + garde-fous).

---

## Référence document

- **Tableau révisé**: WAANGU_ERP_REVISE_LE_10_02_2026-V3.md (microservices, micro-frontends, technologies, répartition des tâches).
- **Chapitre 0 version finale**: CHAPITRE-00-VERSION-FINALE.md (détails, codes, scripts, garde-fous).
