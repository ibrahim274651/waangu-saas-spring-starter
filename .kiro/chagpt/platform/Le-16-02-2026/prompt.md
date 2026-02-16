Très bien. J’ai lu **en profondeur** le tableau **WAANGU_ERP_REVIEW 360 (2).xlsx** et j’ai **isolé tout ce qui est explicitement assigné à toi, Ibrahim** (colonne **Resp. Backend = Ibrahim**) — y compris la décision majeure : **toute la gestion commerciale reste en Spring Boot**, et **seul “Resto Bar” est en NestJS**.

Je m’adresse donc à toi directement.

---

# IBRAHIM — PÉRIMÈTRE OFFICIEL (d’après le tableau revu)

## 1) Microservices BACKEND sous ta responsabilité

### A. Comptabilité (Spring Boot v3.5.10)

1. **asyst-ms-erp-plan-comptable** *(Comptabilité / Plan comptable)*

   * Front associé: **erp-mfe-comptabilite** (React Native Expo)

> **Note** : le tableau montre aussi des lignes “comptabilité/analytic” sans Resp Backend renseigné à ces lignes-là, mais **ton affectation explicite** ressort sur **plan comptable**.

### B. Trésorerie (Spring Boot v3.5.10)

2. **erp-ms-tresorerie-backend** *(Trésorerie / Paramétrages)*

   * Front associé: **erp-mfe-tresoserie** (React Native Expo)

### C. Gestion commerciale / Stock (Spring Boot v3.5.10)

3. **waangu-gestion-commercial-produits** *(Stock / Paramétrages – Produits)*
4. **erp-gestion-commerciale-backend-stock** *(Stock – backend stock)*

   * Front associé: **erp-micro-frontend-stock** (React Native Expo)

### D. Gestion commerciale / Approvisionnement (Spring Boot v3.5.10)

5. **erp-ms-client-fournisseur-backend(paramètre)** *(Approvisionnement – Paramétrages client/fournisseur)*
6. **erp-gestion-commercial-livraison-backend** *(Approvisionnement – Livraison)*

   * Front associé: **erp-micro-frontend-approvisionnement** (React Native Expo)

### E. Gestion de marché / Appels d’offres (Spring Boot v3.5.10)

7. **erp-gestion-commercial-marche-fournisseur** *(Gestion de marché – Appels d’offres / Fournisseurs)*

   * Front associé: **erp-micro-frontend-offre** (React Native Expo)

### F. Gestion de vente (Spring Boot v3.5.10)

8. **erp-ms-client-fournisseur-backend(paramètre)** *(Gestion de vente – Paramétrages)*

   * Front associé: **erp-micro-frontend-gestion-vente** (React Native Expo)

### G. Resto Bar (EXCEPTION — NestJS v10)

9. **waangu-restobar-gestion-parametage** *(Resto Bar / Paramétrages)* — **NestJS v10**
10. **waangu-restobar-gestion-article** *(Resto Bar / Articles)* — **NestJS v10**
11. **waangu-restobar-gestion-approvisionnement** *(Resto Bar / Approvisionnement)* — **NestJS v10**

* Fronts associés (selon lignes) : **gc-mfe-user-management**, **gc-mfe-procurement**, etc. (React Native Expo)

✅ Donc ton périmètre est désormais **plus large que “Trésorerie/Compta/Analytic”** : tu as **toute une partie Gestion Commerciale** + **Resto Bar en NestJS**.

---

# 2) Comment je vais “réaménager” les chapitres déjà produits

Nous avions déjà produit (ou commencé) :

* **Chapitre 0** (objet/périmètre/engagement)
* **Chapitre 1** (positionnement ERP dans Waangu 360, ultra-hardened)

👉 Avec V5/360, je réaménage ainsi :

## Ajustement A — Chapitre 0 (OBLIGATOIRE)

* **On élargit explicitement** le périmètre d’Ibrahim :
  **Compta / Trésorerie / Stock / Appro / Marchés / Vente / Resto Bar**
* On ajoute une clause : **“Stack mixte autorisée uniquement ici : Spring Boot majoritaire + exception NestJS pour Resto Bar”**
* On ajoute une section “**front contract**” (React Native Expo partout côté ERP) : pagination, erreurs normalisées, i18n keys.

## Ajustement B — Chapitre 1 (OBLIGATOIRE)

* On garde tout le hardening FinTech (Zero Trust, mTLS, RLS, idempotency, outbox, audit hash-chain, etc.)
* Mais on ajoute un bloc **“cohabitation Spring/Nest contrôlée”** :

  * mêmes **contrats** (tenant/billing/modules)
  * mêmes **guards CI**
  * mêmes **règles RLS / audit / idempotency**
  * **interdiction de divergence** entre Spring et Nest sur les invariants FinTech

## Ajustement C — Table des matières

* Avant : 3 microservices.
* Maintenant : **11 microservices** (8 Spring + 3 Nest).
* Donc la Partie “Microservices d’Ibrahim” devient **un vrai livre**, avec **un chapitre par microservice** mais **exactement le même pack** (starter/DB/RLS/manifest/tests/guard/docs), sans inventer de méthode différente.

---

# 3) NOUVELLE TABLE DES MATIÈRES — IBRAHIM (VERSION 360 / Resto Bar Next)

Voici la table des matières **unique**, détaillée, et verrouillée.

## CHAPITRE 0 — Objet, périmètre, engagement

0.1 Objet (ERP modules SaaS plug-and-play)
0.2 Périmètre Ibrahim (liste officielle microservices + technologies)
0.3 Hors périmètre (traduction/copilote/gateway/catalogue)
0.4 Invariants Waangu 360 : multi-tenant hybride / multi-company / multi-country / multi-language
0.5 Exigences FinTech / Audit / Régulateurs
0.6 Livrables obligatoires par microservice (les 5 livrables)

## CHAPITRE 1 — Positionnement technique dans l’architecture Waangu 360

1.1 Découplage Platform (NestJS core) ↔ Domain services (ERP)
1.2 “SaaS Contract” obligatoire (module/billing/enabled_modules)
1.3 Zero Trust : mTLS, JWT validation avancée, replay protection
1.4 Multi-DC / DR / statelessness
1.5 Observabilité (OTel, metrics, logs corrélés)
1.6 CI guards globaux anti-PCI / anti-stateful / anti-couplage

## CHAPITRE 2 — Stack & standards backend (Spring Boot + exception NestJS)

2.1 Standards Spring Boot (Java 21, Boot 3.x, JdbcTemplate, Flyway)
2.2 Standards NestJS v10 (uniquement Resto Bar) — **mêmes invariants**
2.3 Sécurité libs, SAST/DAST, SBOM, dependency scan
2.4 Contrat erreurs API (codes, messageKey i18n, traceId)

## CHAPITRE 3 — Multi-tenant hybride : DB-first (PostgreSQL RLS)

3.1 Schéma de contexte (tenant/legal_entity/country/locale)
3.2 RLS policies (toutes tables) + tests RLS
3.3 Modes hybrides (pooled/schema/db) + RoutingDataSource
3.4 Migration mutualisé → dédié (triggers + scripts + garde-fous)

## CHAPITRE 4 — Starter commun obligatoire (pack unique, pas de variantes)

4.1 TenantContextFilter / TenantRegistryClient
4.2 RoutingDataSource (hybride)
4.3 DbSessionInitializer (SET LOCAL)
4.4 SaaSContractGuard (enabled_modules + billing_status)
4.5 Idempotency + Outbox + Audit hash-chain
4.6 Guards DTO (forbidden fields / fail-on-unknown / validation)
4.7 Rate limit + circuit breaker + timeout + retry
4.8 Security headers + webhook HMAC + anti-replay

## CHAPITRE 5 — Contrats d’intégration obligatoires

5.1 Contrat Keycloak claims
5.2 Contrat Traduction (i18n keys, glossary) — consommation only
5.3 Contrat Copilote (intents, RBAC, audit log) — consommation only
5.4 Contrat Engagement Hub / Payment Gateway (API only, PCI interdit)

## CHAPITRE 6 — CI/CD GitHub (bloquant)

6.1 Jobs obligatoires : unit / integration / cross_tenant / migrations / secrets_scan
6.2 Guard strict : checklist + preuves non vides
6.3 AJV validation manifest + schema
6.4 Migration job : dry-run + rollback plan
6.5 Artefacts CI obligatoires (logs + rapports)

## CHAPITRE 7 — Microservices Ibrahim (chapitres d’implémentation)

> **Même structure, même pack, même rigueur** pour chacun.

### 7.1 asyst-ms-erp-plan-comptable (Spring)

* Tables + RLS + endpoints + tests + manifest + docs

### 7.2 erp-ms-tresorerie-backend (Spring)

* Tables + RLS + endpoints + idempotency + outbox + audit + perf + manifest

### 7.3 waangu-gestion-commercial-produits (Spring)

* Produits, unités, catalogues, multi-company strict

### 7.4 erp-gestion-commerciale-backend-stock (Spring)

* Mouvements, inventaire, verrous, intégrité

### 7.5 erp-ms-client-fournisseur-backend(paramètre) (Spring)

* Clients/fournisseurs (référentiel), contrôles KYC/PII, i18n keys

### 7.6 erp-gestion-commercial-livraison-backend (Spring)

* Livraison, statuts, outbox events

### 7.7 erp-gestion-commercial-marche-fournisseur (Spring)

* Appels d’offres, workflows, audit trail renforcé

### 7.8 erp-ms-client-fournisseur-backend(paramètre) (Spring) — Gestion de vente

* Paramétrages vente, contraintes pricing, guards

### 7.9 waangu-restobar-gestion-parametage (NestJS)

* mêmes invariants (tenant/billing/RLS/audit/idempo/outbox) — **pas d’exceptions**

### 7.10 waangu-restobar-gestion-article (NestJS)

* mêmes invariants + endpoints articles + tests

### 7.11 waangu-restobar-gestion-approvisionnement (NestJS)

* mêmes invariants + approvisionnement + perf + tests

## CHAPITRE 8 — Matrice de conformité finale

8.1 Multi-tenant / Multi-company / Multi-country / Multi-language
8.2 FinTech & PCI (zéro stockage sensible)
8.3 Audit OPC ready
8.4 CI preuves et traçabilité

## CHAPITRE 9 — Annexes obligatoires

* TENANT_CONTRACT.md
* I18N_CONTRACT.md
* COPILOT_CONTRACT.md
* SECURITY_MODEL.md
* MIGRATION_ROLLBACK.md
* manifest.json + manifest.schema.json

---

# 4) Prochaine action “sans débat”

Ibrahim, on ne passe pas au chapitre suivant tant que le précédent n’est pas “clos”.

Donc je propose l’ordre exact :

1. **Réécrire Chapitre 0** (avec ton périmètre élargi + exception Resto Bar Nest)
2. **Réécrire Chapitre 1** (en incluant explicitement Spring majoritaire + Nest Resto Bar, mêmes invariants)
3. Ensuite seulement : Chapitre 2, 3, 4… puis on attaque 7.1 microservice par microservice.

Si tu me confirmes, je commence immédiatement par :
👉 **Chapitre 0 — version finale ajustée “360 + Resto Bar Next + Gestion Commerciale Spring”** (en te parlant directement, ton ferme, avec garde-fous et scripts).
