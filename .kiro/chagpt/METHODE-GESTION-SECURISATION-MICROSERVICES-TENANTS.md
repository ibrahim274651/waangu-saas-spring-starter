# Méthode recommandée — Gestion et sécurisation des microservices et tenants

**Waangu 360 — Référentiel unique**  
**Basé sur** : stratégie SaaS (chagpt), Bible ERP, normes audit, cahiers Ibrahim, contrats, CI  
**Applicable à** : tous les microservices backend ERP (Ibrahim + futurs) et à tous les tenants

---

## 1. Principe directeur : une seule méthode, zéro exception

La **meilleure méthode** pour gérer et sécuriser tous les microservices et tenants est d’appliquer **une seule ossature** à tous les services et d’**imposer des garde-fous automatiques** (CI, RLS, contrats). Aucun microservice ne doit entrer en production sans passer par cette méthode.

### Bénéfices

- **Cohérence** : même modèle multi-tenant, même sécurité, même audit partout.  
- **Risque maîtrisé** : fuite cross-tenant, double-spend, écritures modifiables évités par construction.  
- **Scalabilité** : ajout de nouveaux microservices par copier-coller de l’ossature + remplissage métier.  
- **Conformité** : OPC, ISA/ISQM, FinTech, ISO/SOC2 soutenus par les mêmes preuves (RLS, audit, idempotency).

---

## 2. Les quatre piliers (obligatoires pour tout microservice)

| Pilier | Rôle | Référence chagpt |
|--------|------|-------------------|
| **1. Plateforme (starter + contrats)** | Un seul starter Spring (George), contrats tenant / i18n / copilot / gateway. Aucune duplication de logique plateforme dans les MS. | 03, 05, 08, 12 |
| **2. Données (tenant + RLS)** | `tenant_id` + `legal_entity_id` (si financier) + `country_code` partout ; RLS sur toutes les tables ; preuve cross-tenant. | 00, 04, 06, 09, 10 |
| **3. FinTech / Audit** | Idempotency, outbox, audit trail hash chain, immutabilité des écritures postées, period lock si compta. | 02, 04, 08, 09, 10 |
| **4. CI / Qualité** | Jobs obligatoires (unit, integration, cross_tenant, migrations, secrets_scan), guard PR (checklist + preuves), pas de merge si rouge. | 07, 11, 12 |

**Règle** : tout nouveau microservice (Ibrahim ou autre) doit satisfaire les quatre piliers avant mise en production.

---

## 3. Gestion des tenants (cycle de vie et sécurité)

### 3.1 Source de vérité : Tenant Registry

- **Un seul registre** (service ou BDD dédiée) qui pour chaque tenant stocke :
  - `tenant_id`, mode (`POOLED` | `SCHEMA` | `DEDICATED_DB`), statut (`ACTIVE` | `TRIAL` | `SUSPENDED` | `TERMINATED`)
  - `legal_entity_id`(s), `country_code`, paramètres (locale, timezone)
  - modules activés (`enabled_modules`), `subscription_id`, `billing_status`
  - infos de routage BDD (JDBC URL, schema, secret ref) pour modes SCHEMA / DEDICATED_DB

- Chaque microservice **ne décide jamais** du mode ou du statut : il les reçoit via JWT (Keycloak) ou via un appel au Tenant Registry (côté API Gateway / BFF).

### 3.2 Sécurité par couches (de l’entrée jusqu’à la BDD)

1. **JWT (Keycloak)**  
   Claims obligatoires : `tenant_id`, `tenant_mode`, `legal_entity_id`, `country_code`, `locale`, `billing_status`, `enabled_modules`, `user_id`, `roles`, `correlation_id`.  
   → Refus 401 si `tenant_id` ou contexte essentiel manquant.

2. **Filtre applicatif (TenantContextFilter + SaaSContractGuard)**  
   - Vérification `billing_status` (403 si SUSPENDED/TERMINATED).  
   - Vérification module dans `enabled_modules` (403 si module désactivé).  
   - Interdiction des champs `tenant_id` / `legal_entity_id` dans le body (ForbiddenBodyFieldsFilter).  
   → Contexte tenant stocké dans TenantContextHolder pour la requête.

3. **Base de données (RLS)**  
   - Au début de chaque transaction : `SET LOCAL app.current_tenant`, `app.current_legal_entity`, `app.current_country` (et `search_path` en mode SCHEMA).  
   - Toutes les tables métier ont une policy du type :  
     `USING (tenant_id = current_setting('app.current_tenant')::uuid [AND legal_entity_id = ...])`  
   → Même en cas de bug applicatif, la BDD ne renvoie que les lignes du tenant courant.

4. **Guards métier**  
   - `@FinancialEndpoint` : refus si `legal_entity_id` absent.  
   - Immutabilité : refus 409 si modification d’une entité déjà postée.  
   - Period lock : refus 409 si écriture sur période verrouillée.

**Règle** : aucune requête ne doit atteindre la BDD sans que le contexte tenant ait été fixé (DbSessionInitializer / SET LOCAL).

### 3.3 Gestion du cycle de vie tenant

| Événement | Action recommandée |
|-----------|--------------------|
| **Création tenant** | Onboarding (hors périmètre Ibrahim) : création dans Tenant Registry, choix du mode, activation des modules. Les MS ne font que lire le contexte. |
| **Changement de mode** (POOLED → SCHEMA ou DEDICATED_DB) | Mise à jour du Tenant Registry + migration des données (scripts dédiés, fenêtre de maintenance). Les MS utilisent le nouveau routage. |
| **Suspension** | `billing_status = SUSPENDED` dans le registre / Keycloak. Tous les MS refusent 403 (SaaSContractGuard). |
| **Résiliation** | `billing_status = TERMINATED` ; désactivation des modules ; après délai légal, archivage / purge selon politique. |
| **Ajout / retrait de module** | Mise à jour `enabled_modules`. Les MS refusent 403 pour les modules désactivés. |

---

## 4. Gestion des microservices (onboarding et conformité)

### 4.1 Registre des microservices (catalogue interne)

- **Fichier ou base** listant tous les microservices avec :
  - `service_id`, `module_id`, `owner`, `type` (core / plugin)
  - `repo`, `manifest.json` (lien ou contenu validé)
  - dépendances (starter, autres MS), permissions

- Chaque microservice **expose** un `manifest.json` validé par `manifest.schema.json` (AJV en CI).  
- Le catalogue SaaS / facturation s’appuie sur ce manifest (modules activables, facturables).

### 4.2 Checklist d’onboarding d’un nouveau microservice

Avant qu’un **nouveau** microservice (ou un existant non encore aligné) soit considéré conforme :

- [ ] **Starter** : dépendance `waangu-saas-spring-starter` ; aucune duplication de TenantContextFilter, RoutingDataSource, DbSessionInitializer, IdempotencyService, AuditLogService, OutboxService, I18nClient.
- [ ] **Modèle de données** : `tenant_id` (et `legal_entity_id` si financier, `country_code` si localisable) sur toutes les tables métier ; champs i18n (`*_i18n_key`, `*_source`) pour les libellés.
- [ ] **Migrations** : V1 tables, V2 RLS, V3 audit/outbox/idempotency si pas déjà dans un V commun ; convention Flyway.
- [ ] **RLS** : policies sur **toutes** les tables tenant-scopées ; script de vérification RLS en CI (échec si table sans policy).
- [ ] **Tests** : unit, integration, **cross_tenant** (Tenant A ≠ Tenant B ⇒ 0 ligne), idempotency si POST critique, immutability/period lock si applicable.
- [ ] **Contrats** : TENANT_CONTRACT, (I18N, COPILOT, SECURITY_MODEL selon besoin), MIGRATION_ROLLBACK ; manifest.json + manifest.schema.json.
- [ ] **CI** : jobs unit, integration, cross_tenant, migrations (avec Flyway + RLS check + AJV manifest), secrets_scan ; guard PR (checklist + preuves, migration/rollback si SQL modifié).
- [ ] **Sécurité** : pas de secrets en clair ; pas d’acceptation de `tenant_id`/`legal_entity_id` dans le body ; billing et module vérifiés en entrée.

**Règle** : tout microservice qui ne coche pas cette checklist reste **hors production** (ou en zone dédiée de démo/test uniquement).

### 4.3 Réutilisation (copier-coller) pour les futurs microservices

- **Réutiliser** : même structure de packages (config, controller, service, repository, domain, guard, audit, outbox, idempotency), même convention de migrations (V1–V4), même `ci.yml` et guard PR, mêmes contrats.
- **Adapter** : uniquement le métier (tables, APIs, règles d’immutabilité, règles de period lock si besoin).
- **Ne pas inventer** : pas de nouveau filtre tenant “maison”, pas de nouveau système d’idempotency ; tout passe par le starter et les contrats.

---

## 5. Sécurisation opérationnelle (runtime et données)

### 5.1 Secrets et configuration

- **Secrets** : jamais en clair dans le code ni dans le repo. Utiliser des variables d’environnement, un coffre (AWS Secrets Manager, HashiCorp Vault, etc.) ou des secrets Kubernetes.  
- **CI** : job `secrets_scan` (ex. Gitleaks) obligatoire ; échec si secret détecté.  
- **Rotation** : politique de rotation des secrets (ex. 90 jours) ; documentée dans SECURITY_MODEL.

### 5.2 Réseau et périmètres

- **Segmentation** : microservices dans un réseau interne ; pas d’exposition directe de la BDD vers l’internet.  
- **Egress** : autoriser uniquement les appels nécessaires (Keycloak, Tenant Registry, Translation, Copilot, Engagement Hub, Payment Gateway) ; tout le reste refusé par défaut.  
- **TLS** : TLS 1.3 pour toutes les communications (client ↔ API, API ↔ services internes).

### 5.3 Audit et traçabilité

- **Audit log** : table append-only avec `tenant_id`, `legal_entity_id`, `actor_user_id`, `action`, `entity_type`, `entity_id`, `correlation_id`, `payload`, `occurred_at`, `prev_hash`, `curr_hash`.  
- **Hash chain** : chaque entrée calcule `curr_hash` à partir de la précédente ; aucune modification/suppression des lignes.  
- **Logs applicatifs** : inclure `tenant_id`, `correlation_id` (et si possible `legal_entity_id`) dans les logs structurés ; pas de fuite de données sensibles en clair.

### 5.4 Surveillance et alertes

- **Métriques** : par tenant et par microservice (requêtes, erreurs 4xx/5xx, latence).  
- **Alertes** : seuils sur erreurs, latence, taux de refus 403 (billing/module).  
- **Incidents** : procédure en cas de suspicion de fuite cross-tenant ou de compromission (isolation du tenant, revue des logs et de l’audit).

---

## 6. Minimisation des risques (garde-fous techniques)

### 6.1 Ce qui doit bloquer le merge (CI / guard)

- Pipeline CI rouge (unit, integration, cross_tenant, migrations, secrets_scan).  
- Checklist PR incomplète (ex. 20 points ou équivalent).  
- Preuves CI vides (liens vers jobs / artefacts manquants).  
- Fichiers SQL modifiés sans section Migration / Rollback remplie.  
- Manifest invalide (AJV en échec).  
- Table métier sans policy RLS (script de vérification en échec).  
- Duplication de classes du starter (détection par script ou revue).

### 6.2 Ce qui doit bloquer la mise en production

- Un des 10 points de garantie (multi-tenant, multi-sociétés, multi-pays, multi-langues, RLS, immutabilité si applicable, idempotency, outbox, audit hash chain, CI hard guard) non satisfait.  
- Absence de TENANT_CONTRACT (et contrats annexes selon le MS).  
- Absence de plan de migration/rollback pour les changements de schéma.

### 6.3 Revue et responsabilités

- **CODEOWNERS** : chemins critiques (migrations, config sécurité, manifest) revus par Platform / QA / DevOps.  
- **Branch protection** : pipelines obligatoires, nombre d’approbations, résolution des conversations.  
- **Audit** : les logs de merge et les artefacts CI servent de preuve pour OPC / ISA / ISQM.

---

## 7. Synthèse : la méthode en une page

| Domaine | Méthode recommandée |
|---------|---------------------|
| **Tenants** | Un seul Tenant Registry ; contexte injecté par JWT / Gateway ; pas de décision de mode dans les MS. |
| **Sécurité** | 4 couches : JWT → Filtre (billing, module, body interdit) → RLS (SET LOCAL + policies) → Guards métier. |
| **Microservices** | Un seul starter ; une seule structure repo ; une seule convention migrations/CI/contrats ; onboarding par checklist. |
| **Données** | `tenant_id` (+ `legal_entity_id`, `country_code`) partout ; RLS sur toutes les tables ; preuve cross-tenant en CI. |
| **FinTech / Audit** | Idempotency + outbox + audit hash chain + immutabilité + period lock (si compta) ; pas de contournement. |
| **CI / Qualité** | Jobs obligatoires + guard PR + RLS check + AJV manifest ; merge et prod bloqués si non conforme. |
| **Opérations** | Secrets dans un coffre ; réseau segmenté ; TLS ; audit log immuable ; métriques et alertes par tenant. |

**En une phrase** : un seul modèle (starter + contrats + RLS + audit + idempotency + outbox), une seule checklist d’onboarding, des garde-fous automatiques en CI et en runtime, et aucun microservice ni tenant en production sans respect de cette méthode.

---

## 8. Référence rapide vers les documents chagpt

| Besoin | Document |
|--------|----------|
| Stratégie SaaS, modes tenant, régions | 00-WAANGU-360-SAAS-STRATEGY.md |
| CORE vs PLUGINS, règles ERP | 01-BIBLE-ERP-CORE-VS-PLUGINS.md |
| Normes audit (ISA, IFRS, IPSAS, ISQM) | 02-NORMES-AUDIT-ISA-IFRS-IPSAS.md |
| Cadre général Ibrahim, livrables | 03-IBRAHIM-CAHIER-DES-CHARGES-GENERAL.md |
| Specs trésorerie (MS#1) | 04-IBRAHIM-MS1-TRESORERIE-SPECS.md |
| Code Spring MS#1 | 05-IBRAHIM-MS1-CODE-SPRING.md |
| Migrations SQL + RLS | 06-IBRAHIM-MS1-SQL-MIGRATIONS.md |
| CI GitHub (ci.yml, guard) | 07-IBRAHIM-MS1-GITHUB-CI.md |
| Contrats (Tenant, I18N, Copilot, Security, Migration) | 08-IBRAHIM-MS1-CONTRACTS-DOCS.md |
| Specs compta (MS#2) | 09-IBRAHIM-MS2-COMPTABILITE-SPECS.md |
| Specs analytique (MS#3) | 10-IBRAHIM-MS3-ANALYTIC-SPECS.md |
| Template PR + checklist | 11-IBRAHIM-GITHUB-PR-TEMPLATE.md |
| Récap complet Ibrahim | 12-RECAP-IBRAHIM-COMPLETE.md |

Ce document (**METHODE-GESTION-SECURISATION-MICROSERVICES-TENANTS.md**) sert de **référentiel unique** pour la gestion et la sécurisation de **tous** les microservices et tenants Waangu 360, actuels et futurs.
