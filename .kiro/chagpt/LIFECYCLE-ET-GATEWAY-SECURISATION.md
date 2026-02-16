# Lifecycle à suivre et Gateway pour appliquer toute la sécurisation

**Waangu 360 — Basé sur l’ensemble des échanges chagpt**  
**Objectif** : Un **cycle de vie** clair (tenant + microservice) à suivre, et un **gateway** qui applique toute la sécurisation avant que la requête n’atteigne les microservices.

---

## 1. Pourquoi un lifecycle et un gateway sont nécessaires

- **Lifecycle** : sans étapes définies (création tenant, onboarding MS, suspension, etc.), les équipes font chacun à sa façon → incohérence, oublis de sécurité, non-conformité.
- **Gateway** : si chaque microservice refait JWT + billing + module tout seul, on multiplie les erreurs et les failles. Un **point d’entrée unique** (API Gateway ou BFF) permet d’**appliquer une seule fois** toute la sécurisation (auth, tenant, billing, modules) et d’**injecter un contexte fiable** vers les MS.

**Proposition** : adopter un **lifecycle officiel** (tenant + microservice) et **implémenter un gateway** qui centralise la sécurisation ; les microservices derrière ne reçoivent que des requêtes déjà validées et enrichies.

---

## 2. Lifecycle tenant (à suivre)

Étapes à respecter pour tout tenant, de la création à la résiliation.

| Phase | Étape | Qui fait quoi | Sécurisation / Gateway |
|-------|--------|----------------|-------------------------|
| **1. Création** | Demande d’onboarding (formulaire / API admin) | Équipe plateforme / onboarding | — |
| **2. Provisioning** | Création dans Tenant Registry : tenant_id, mode (POOLED par défaut), billing_status=TRIAL ou ACTIVE, enabled_modules, country_code, legal_entity_id(s) | Plateforme / script | Gateway et MS ne voient que des tenants existants dans le Registry |
| **3. Identité** | Création du realm / client Keycloak (ou équivalent) ; attribution des claims (tenant_id, tenant_mode, legal_entity_id, country_code, locale, billing_status, enabled_modules, roles) | Plateforme / IAM | Gateway valide le JWT émis par Keycloak |
| **4. Routage BDD** | Si mode SCHEMA ou DEDICATED_DB : création schéma ou BDD, enregistrement JDBC URL / schema / secret dans Tenant Registry | Plateforme / DevOps | Les MS utilisent le Tenant Registry (via starter) pour obtenir l’URL ; le gateway ne gère pas la BDD |
| **5. Actif** | Tenant en ACTIVE ou TRIAL ; trafic passe par le Gateway → JWT valide, billing OK, module activé → requête transmise aux MS | Utilisateur / appels API | **Gateway** : vérifie JWT, billing_status, enabled_modules ; injecte/enrichit headers ; route vers le bon MS |
| **6. Suspension** | billing_status = SUSPENDED dans Tenant Registry / Keycloak | Plateforme / facturation | **Gateway** : rejette 403 pour tout appel (ou BFF renvoie 403) ; les MS ne reçoivent plus de requêtes métier |
| **7. Reprise** | billing_status = ACTIVE à nouveau | Plateforme | Gateway accepte à nouveau les requêtes |
| **8. Résiliation** | billing_status = TERMINATED ; désactivation des modules ; après délai légal : archivage / purge selon politique | Plateforme / juridique | Gateway rejette 403 ; données archivées ou purgées hors du trafic normal |

**Règle** : aucune requête métier ne doit atteindre un microservice sans être passée par le gateway (ou BFF) qui applique les contrôles du lifecycle (tenant valide, actif, module activé).

---

## 3. Lifecycle microservice (à suivre)

Étapes pour qu’un microservice soit autorisé à recevoir du trafic en production.

| Phase | Étape | Critère de passage | Sécurisation / Gateway |
|-------|--------|---------------------|-------------------------|
| **1. Développement** | Code + usage du starter (pas de duplication), modèle de données avec tenant_id / legal_entity_id / country_code, migrations V1–V4 (dont RLS) | Conformité 4 piliers (starter, données, FinTech/audit, CI) | — |
| **2. CI** | Jobs unit, integration, cross_tenant, migrations (Flyway + RLS check + AJV manifest), secrets_scan ; guard PR (checklist, preuves, Migration/Rollback si SQL) | Pipeline vert ; guard vert | — |
| **3. Enregistrement** | MS enregistré dans le catalogue / registre interne ; manifest.json validé ; route exposée (ex. /api/erp/treasury, /api/erp/gl) | Manifest valide ; route déclarée | **Gateway** : ne route que vers des routes connues et déclarées (whitelist par path) |
| **4. Déploiement** | Déploiement en env (test, préprod, prod) ; variables d’environnement (Tenant Registry URL, etc.) ; pas de secrets en clair | Déploiement reproductible ; secrets dans un coffre | Gateway route vers l’URL du MS (service discovery ou config) |
| **5. Trafic** | Requêtes entrantes : Client → Gateway → (vérif JWT, billing, module) → MS | Seul trafic ayant passé le gateway (ou BFF) atteint le MS | **Gateway** applique toute la sécurisation d’entrée ; les MS font confiance au contexte injecté (et refont RLS en BDD) |
| **6. Retrait** | Module désactivé pour un tenant : enabled_modules mis à jour ; le gateway renvoie 403 pour ce module. Décommissionnement du MS : retrait de la route au gateway | Pas de trafic vers un MS décommissionné | Gateway retire la route ou renvoie 404/503 |

**Règle** : aucun microservice ne doit être exposé en production sans être passé par les phases 1–4 et sans être atteignable **uniquement via le gateway** (pas d’accès direct depuis l’extérieur).

---

## 4. Gateway : rôle et ce qu’il doit faire pour avoir “toute la sécurisation”

Le gateway (API Gateway ou BFF) est le **seul point d’entrée** pour les appels clients vers les microservices. Il **doit** implémenter les contrôles suivants pour que toute la sécurisation définie dans nos échanges soit appliquée à un seul endroit.

### 4.1 Ce que le gateway fait (obligatoire)

| # | Contrôle | Comportement | Si échec |
|---|----------|--------------|----------|
| 1 | **Authentification** | Valider le JWT (signature, issuer, expiration) ; extraire les claims. | 401 Unauthorized |
| 2 | **Tenant obligatoire** | Vérifier la présence de tenant_id (et si besoin tenant_mode, legal_entity_id, country_code, locale). | 401 si manquant |
| 3 | **Billing** | Vérifier billing_status ; accepter uniquement ACTIVE ou TRIAL. | 403 Tenant suspended / Billing required |
| 4 | **Module activé** | Pour la route demandée, vérifier que le module correspondant est dans enabled_modules (ex. /api/erp/treasury → ERP_TREASURY_CORE). | 403 Module disabled |
| 5 | **Enrichissement** | Enrichir la requête vers le MS avec des headers (ou re-signer un JWT interne) contenant : tenant_id, legal_entity_id, country_code, locale, user_id, correlation_id, roles. Les MS ne lisent que ce contexte. | — |
| 6 | **Correlation ID** | Générer ou propager X-Correlation-Id pour la traçabilité. | — |
| 7 | **Routage** | Router vers le bon microservice selon le path (ex. /api/erp/treasury → erp-ms-tresorerie-backend). | 404 si route inconnue |
| 8 | **Pas de body forgé** | Optionnel au gateway : rejeter les requêtes dont le body contient tenant_id ou legal_entity_id (ou déléguer au MS ForbiddenBodyFieldsFilter). | 400 Forbidden field |

### 4.2 Ce que le gateway ne fait pas (reste dans les MS)

- **RLS** : reste dans chaque microservice (SET LOCAL + policies PostgreSQL).
- **Idempotency** : gérée dans le MS (header Idempotency-Key transmis par le gateway).
- **Audit log / outbox** : dans le MS (écriture en BDD).
- **Guards métier** (immutabilité, period lock) : dans le MS.

**Résumé** : le gateway assure **auth + tenant + billing + module + enrichissement + routage**. Les MS assurent **RLS + idempotency + audit + guards métier**.

### 4.3 Schéma du flux (sécurisation)

```
Client
  │
  ▼
┌─────────────────────────────────────────────────────────────────┐
│  API GATEWAY (ou BFF)                                            │
│  1. Valider JWT → 401 si invalide                                 │
│  2. Vérifier tenant_id → 401 si absent                           │
│  3. Vérifier billing_status (ACTIVE/TRIAL) → 403 si non          │
│  4. Vérifier enabled_modules pour la route → 403 si module off    │
│  5. Enrichir requête (headers / JWT interne)                      │
│  6. Router vers le microservice                                  │
└─────────────────────────────────────────────────────────────────┘
  │
  ▼
Microservice (ex. erp-ms-tresorerie-backend)
  │  • Lit le contexte (TenantContextHolder ou headers)
  │  • DbSessionInitializer → SET LOCAL app.current_tenant, etc.
  │  • RLS appliqué en BDD
  │  • Idempotency, audit, outbox, guards métier
  ▼
Réponse → Gateway → Client
```

---

## 5. Proposition à poser en chat (texte à copier-coller)

Tu peux suggérer en conversation ce qui suit pour aligner tout le monde sur le lifecycle et le gateway :

---

**Proposition — Lifecycle et Gateway pour la sécurisation**

Nous nous basons sur l’ensemble de nos échanges (stratégie SaaS, Bible ERP, méthode de gestion et sécurisation des microservices et tenants). Pour que toute la sécurisation soit bien appliquée et suivie, nous proposons :

1. **Adopter un lifecycle officiel** :
   - **Tenant** : Création → Provisioning (Tenant Registry + Keycloak) → Actif → (Suspension / Reprise) → Résiliation. Aucune requête métier vers un microservice sans tenant valide et actif.
   - **Microservice** : Développement (4 piliers) → CI (jobs + guard) → Enregistrement (manifest + route) → Déploiement → Trafic uniquement via le gateway. Pas d’exposition directe des MS en production.

2. **Implémenter un gateway (API Gateway ou BFF)** qui centralise la sécurisation :
   - Valider le JWT et refuser si tenant_id ou contexte essentiel manquant (401).
   - Vérifier billing_status (403 si SUSPENDED/TERMINATED).
   - Vérifier que le module est dans enabled_modules pour la route demandée (403 si désactivé).
   - Enrichir la requête avec le contexte tenant (headers ou JWT interne) et router vers le bon microservice.
   - Générer ou propager le correlation_id.

   Ainsi, **toute la sécurisation d’entrée** (auth, tenant, billing, modules) est appliquée au gateway ; les microservices derrière appliquent RLS, idempotency, audit et guards métier.

3. **Règle** : aucune requête métier ne doit atteindre un microservice sans être passée par ce gateway (ou BFF). Les MS ne sont pas exposés directement à l’extérieur.

Souhaitez-vous que nous détaillions les spécifications du gateway (routes, mapping module ↔ path, format des headers) et les étapes exactes du lifecycle (scripts, responsabilités) ?

---

## 6. Checklist rapide “Lifecycle + Gateway”

- [ ] Lifecycle tenant documenté et partagé (création → actif → suspension → résiliation).
- [ ] Lifecycle microservice documenté (dév → CI → enregistrement → déploiement → trafic via gateway).
- [ ] Gateway (ou BFF) en place comme seul point d’entrée pour les appels vers les MS.
- [ ] Gateway : JWT validé, tenant_id obligatoire, billing_status vérifié, enabled_modules vérifié par route.
- [ ] Gateway : enrichissement du contexte (headers ou JWT interne) et routage vers les MS.
- [ ] Microservices : pas d’exposition directe en prod ; trafic uniquement via le gateway.
- [ ] Tenant Registry et Keycloak alignés avec le lifecycle (création, suspension, résiliation).

---

Ce document peut être utilisé pour **suggérer en chat** qu’un lifecycle clair et un gateway centralisant la sécurisation sont **nécessaires** pour implémenter et faire respecter toute la sécurisation définie dans nos échanges.
