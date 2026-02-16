# Ibrahim — Ce que tu peux faire, résumé des conversations et plan de tâches

**Référence** : prompt.md (Le-16-02-2026) + tout le dossier `platform-document\chagpt`  
**Pour** : Ibrahim (Backend ERP — Spring Boot majoritaire + NestJS Resto Bar)

---

# 1) CE QUE TU PEUX FAIRE (d’après le prompt 360 / Resto Bar Next)

## A. Valider la feuille de route

- **Confirmer** que tu acceptes l’ordre proposé :
  1. Réécrire **Chapitre 0** (périmètre élargi + exception Resto Bar Nest + front contract).
  2. Réécrire **Chapitre 1** (Spring majoritaire + Nest Resto Bar, mêmes invariants).
  3. Puis enchaîner Chapitres 2, 3, 4, 5, 6, puis **7.1 → 7.11** microservice par microservice.

- **Répondre** à la proposition : *« Je confirme, commence par Chapitre 0 version finale ajustée 360 + Resto Bar Next + Gestion Commerciale Spring. »*

## B. Travailler strictement chapitre par chapitre

- **Ne pas passer au chapitre N+1** tant que le chapitre N n’est pas **clos** (livrables faits, garde-fous OK, validation).
- Pour chaque **microservice (7.1 à 7.11)** : appliquer **exactement le même pack** (starter / DB / RLS / manifest / tests / guard / docs), sans inventer une autre méthode.

## C. Respecter la stack et l’exception Nest

- **8 microservices Spring Boot v3.5.10** : Compta (plan comptable), Trésorerie, Gestion commerciale (produits, stock, client-fournisseur, livraison, marché fournisseur, vente).
- **3 microservices NestJS v10** : Resto Bar (paramétrage, article, approvisionnement) — **mêmes invariants** (tenant, billing, RLS, audit, idempotency, outbox) que Spring.
- **Front contract** : React Native Expo partout (pagination, erreurs normalisées, i18n keys) ; tu exposes des APIs conformes, tu ne développes pas les MFE.

## D. Ne pas faire (hors périmètre)

- Traduction automatique (consommation only).
- Copilote IA (intents + RBAC + audit only).
- Catalogue SaaS / Facturation SaaS / Onboarding tenant.
- Moteur Payment Gateway (intégration API only, zéro PCI).

---

# 2) RÉSUMÉ DE TOUTES LES CONVERSATIONS / DOCUMENTS (platform-document\chagpt)

## Stratégie et normes (racine chagpt)

| Document | Contenu en bref |
|----------|------------------|
| **00-WAANGU-360-SAAS-STRATEGY.md** | Stratégie multi-tenant, 4 env, AWS Irlande/Virginie, modes pooled/schema/dedicated. |
| **01-BIBLE-ERP-CORE-VS-PLUGINS.md** | CORE vs PLUGINS ; règle : désactivable sans casser l’ERP = plugin. |
| **02-NORMES-AUDIT-ISA-IFRS-IPSAS.md** | ISA, ISQM, OPC, IFRS, IPSAS ; impact sur CORE et plugins. |
| **03-IBRAHIM-CAHIER-DES-CHARGES-GENERAL.md** | Cadre général, livrables, liste MS (ancienne liste 3 + 4 restants). |
| **METHODE-GESTION-SECURISATION-MICROSERVICES-TENANTS.md** | Méthode unique : 4 piliers, Tenant Registry, 4 couches sécurité, checklist onboarding, blocage merge/prod. |
| **LIFECYCLE-ET-GATEWAY-SECURISATION.md** | Lifecycle tenant + MS ; gateway obligatoire (auth, tenant, billing, module) avant les MS. |
| **20-QUESTIONS-CONTEXTE-PARTAGE-CHAT.md** | 20 questions + bloc contexte pour réutiliser le cadre dans un autre chat. |
| **WAANGU-SAAS-SPRING-STARTER-REF.md** | Référence starter : artifact, usage, composants. |

## Spécifications microservices (ancienne liste 3 MS)

| Document | Contenu |
|----------|--------|
| **04-IBRAHIM-MS1-TRESORERIE-SPECS.md** | Specs trésorerie (tables, APIs, RLS, idempotency, outbox, tests). |
| **05-IBRAHIM-MS1-CODE-SPRING.md** | Code Spring (controllers, services, repos, DTOs) trésorerie. |
| **06-IBRAHIM-MS1-SQL-MIGRATIONS.md** | V1–V4 SQL + RLS pour trésorerie. |
| **07-IBRAHIM-MS1-GITHUB-CI.md** | CI (unit, integration, cross_tenant, migrations, secrets_scan) + guard strict. |
| **08-IBRAHIM-MS1-CONTRACTS-DOCS.md** | TENANT_CONTRACT, I18N, COPILOT, SECURITY_MODEL, MIGRATION_ROLLBACK + manifests. |
| **09-IBRAHIM-MS2-COMPTABILITE-SPECS.md** | Specs compta (écritures, journaux, period lock, immutabilité). |
| **10-IBRAHIM-MS3-ANALYTIC-SPECS.md** | Specs compta analytique (axes, dimensions, allocations). |
| **11-IBRAHIM-GITHUB-PR-TEMPLATE.md** | Template PR + checklist 20 points. |
| **12-RECAP-IBRAHIM-COMPLETE.md** | Récap complet (4 piliers, 10 garanties, livrables, interdictions, DoD). |

## Versions / tables des matières (platform/Le-10, Le-11, Le-16)

| Document | Contenu |
|----------|--------|
| **Le-10-02-2026 v_3** | CHAPITRE-00-VERSION-FINALE, TABLE-DES-MATIERES-DETAILLEE (Parties 0–9). |
| **Le-10-02-2026 v_5** | TABLE-DES-MATIERES-IBRAHIM-V5, IBRAHIM-CAHIER-V5-VERSION-FINALE (3 MS, 10 garanties, structure repo). |
| **Le-11-02-2026** | prompt.md (contexte du jour). |
| **Le-16-02-2026 prompt.md** | **Périmètre 360** : 11 MS (8 Spring + 3 Nest Resto Bar), nouvelle TOC Chapitres 0–9, ajustements Chapitre 0 & 1, ordre de travail. |

## GitHub / microservices (chagpt/github)

| Document | Contenu |
|----------|--------|
| **github/prompt.md** | Contexte repo, starter, CI. |
| **github/microservive/1.md** | Exemples code (BankAccountService, IdempotencyService, OutboxService, AuditLogService). |
| **github/microservive/2.md** | Exemples compta analytique (AxisService, DimensionService, AllocationService, etc.). |

## Synthèse transversale

- **Une seule méthode** pour tous les MS (Spring et Nest) : mêmes contrats (tenant, billing, modules), mêmes guards CI, mêmes règles RLS / audit / idempotency / outbox.
- **Starter** : `waangu-saas-spring-starter` pour Spring ; pour Nest, **mêmes invariants** (équivalent tenant context, RLS, audit, idempotency, outbox) sans dupliquer la logique métier plateforme.
- **5 livrables par microservice** : (1) PR Spring ou Nest conforme, (2) Migrations SQL + RLS, (3) manifest validé, (4) CI green + artefacts, (5) Docs (TENANT_CONTRACT, etc.).
- **Pas de débat** : on ne passe au chapitre suivant qu’une fois le précédent clos.

---

# 3) ORDRE DES TÂCHES (RANGE) — TOUT CE QUE TU AS À FAIRE

## Phase 0 — Cadre (à faire en premier)

| # | Tâche | Qui fait | Statut |
|---|--------|----------|--------|
| 0.1 | **Chapitre 0** — Réécriture version 360 (périmètre élargi 11 MS, stack mixte Spring/Nest, front contract, invariants, 5 livrables) | Rédaction / validation | À faire |
| 0.2 | **Chapitre 1** — Réécriture (découplage Platform/ERP, SaaS Contract, Zero Trust, Spring/Nest mêmes invariants) | Rédaction / validation | À faire |

→ **Tu peux** : confirmer l’ordre et demander la rédaction du Chapitre 0 puis du Chapitre 1.

## Phase 1 — Chapitres transverses (2 à 6)

| # | Tâche | Référence |
|---|--------|-----------|
| 1.1 | Chapitre 2 — Stack & standards (Spring Boot 3.5.10 + NestJS v10, mêmes invariants) | TOC 2.1–2.4 |
| 1.2 | Chapitre 3 — Multi-tenant hybride DB-first (RLS, modes, RoutingDataSource, migration) | TOC 3.1–3.4 |
| 1.3 | Chapitre 4 — Starter commun (filters, datasource, DbSessionInitializer, idempotency, outbox, audit, guards) | TOC 4.1–4.8 |
| 1.4 | Chapitre 5 — Contrats (Keycloak, i18n, Copilot, Engagement Hub / Payment Gateway) | TOC 5.1–5.4 |
| 1.5 | Chapitre 6 — CI/CD GitHub (jobs, guard strict, AJV manifest, migration job, artefacts) | TOC 6.1–6.5 |

→ **Tu les valides** un par un ; pas de passage au 7 tant que 0–6 sont clos.

## Phase 2 — Microservices (7.1 à 7.11) — Un chapitre par MS

Pour **chaque** microservice : même pack (tables + RLS + endpoints + tests + manifest + docs). Ordre suggéré :

| # | Microservice | Stack | Tâche |
|---|--------------|-------|--------|
| 2.1 | **7.1** asyst-ms-erp-plan-comptable | Spring | Tables + RLS + endpoints + tests + manifest + docs |
| 2.2 | **7.2** erp-ms-tresorerie-backend | Spring | Déjà documenté (04–08) ; aligner sur 360 si besoin |
| 2.3 | **7.3** waangu-gestion-commercial-produits | Spring | Produits, unités, catalogues, multi-company |
| 2.4 | **7.4** erp-gestion-commerciale-backend-stock | Spring | Mouvements, inventaire, verrous |
| 2.5 | **7.5** erp-ms-client-fournisseur-backend (paramètre) | Spring | Clients/fournisseurs, KYC/PII, i18n |
| 2.6 | **7.6** erp-gestion-commercial-livraison-backend | Spring | Livraison, statuts, outbox |
| 2.7 | **7.7** erp-gestion-commercial-marche-fournisseur | Spring | Appels d’offres, workflows, audit |
| 2.8 | **7.8** erp-ms-client-fournisseur-backend (vente) | Spring | Paramétrages vente, pricing, guards |
| 2.9 | **7.9** waangu-restobar-gestion-parametage | **NestJS** | Mêmes invariants (tenant, RLS, audit, idempo, outbox) |
| 2.10 | **7.10** waangu-restobar-gestion-article | **NestJS** | Articles + mêmes invariants |
| 2.11 | **7.11** waangu-restobar-gestion-approvisionnement | **NestJS** | Approvisionnement + mêmes invariants |

→ **Tu implémentes** (ou tu fais rédiger les specs) **un MS à la fois** ; tu ne passes au suivant qu’une fois le pack complet et validé.

## Phase 3 — Conformité et annexes

| # | Tâche | Référence |
|---|--------|-----------|
| 3.1 | Chapitre 8 — Matrice de conformité (multi-tenant, FinTech/PCI, audit OPC, CI) | TOC 8.1–8.4 |
| 3.2 | Chapitre 9 — Annexes obligatoires (TENANT_CONTRACT, I18N, COPILOT, SECURITY_MODEL, MIGRATION_ROLLBACK, manifest) | TOC 9 |

---

# 4) RÉSUMÉ EN UN COUP D’ŒIL

- **Tu peux faire** : (1) Confirmer l’ordre Chapitre 0 → 1 → 2… → 7.1…7.11 → 8 → 9. (2) Travailler chapitre par chapitre et MS par MS avec le même pack. (3) Respecter Spring pour 8 MS et Nest pour 3 Resto Bar avec les **mêmes invariants**.
- **Conversations chagpt** : Stratégie SaaS, Bible ERP, normes audit, méthode de sécurisation, lifecycle/gateway, 3 MS déjà détaillés (trésorerie, compta, analytic), PR/CI, récap, tables des matières v3/v5, et **périmètre 360 (11 MS)** dans Le-16-02-2026.
- **Ordre des tâches** : **0** (Chapitre 0 & 1) → **1** (Chapitres 2–6) → **2** (7.1 à 7.11 un par un) → **3** (Chapitres 8 & 9). Ne pas avancer au N+1 tant que N n’est pas clos.

**Prochaine action recommandée** : confirmer et demander : *« Chapitre 0 — version finale ajustée 360 + Resto Bar Next + Gestion Commerciale Spring »*, puis enchaîner sur le Chapitre 1.
