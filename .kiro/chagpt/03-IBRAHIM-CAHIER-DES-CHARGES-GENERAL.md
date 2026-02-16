# 📘 CAHIER DES CHARGES — IBRAHIM (Backend ERP)

**Waangu 360 — Niveau GAFA x10 / Big-4 / Régulateurs / FinTech**

---

## CHAPITRE 1 — CADRAGE GÉNÉRAL & ORDRE DE MISSION

### 1.1 Rôle de Ibrahim (Backend ERP Engineer)

Ibrahim est responsable personnellement de:
1. La transformation SaaS multi-tenant hybride de chaque microservice ERP qui lui est attribué
2. Le respect strict du découpage CORE vs PLUGINS
3. La preuve technique de conformité (tests, scripts, manifests, audits)

⚠️ Il ne s'agit PAS de développer de nouvelles fonctionnalités métier, mais de:
- re-structurer, isoler, modulariser et rendre catalogable l'existant

### 1.2 Objectif final (critère de réussite)

Un microservice ERP géré par Ibrahim est CONFORME si et seulement si:

1. Il peut être:
   - installé
   - activé
   - désactivé
   - suspendu
   - facturé
   par tenant, sans casser le CORE

2. Il fonctionne dans les 3 modes:
   - mutualisé
   - schéma dédié
   - base dédiée

3. Il fournit:
   - isolation des données prouvée
   - audit trail immuable
   - manifest SaaS exploitable par le catalogue Waangu

4. Il est audit-ready (banque, État, Big-4)

---

## 1.3 Environnement d'exécution imposé

Ibrahim doit concevoir comme si le microservice allait être audité demain:

**Environnements**: dev / test / pré-prod / prod

**Régions**:
- AWS Irlande (primaire)
- AWS Virginie (secondaire)
- DC local par pays (data residency)

**Contraintes**:
- migration inter-région possible
- export/restauration par tenant
- PRA / DR compatibles régulateurs

---

## 1.4 Règles d'architecture OBLIGATOIRES

### A. Multi-tenant (NON DISCUTABLE)

**`tenant_id` obligatoire**:
- dans toutes les tables métier
- dans tous les events Kafka
- dans tous les logs

**Le microservice doit refuser toute requête sans tenant valide**

**Le tenant est injecté via**:
- JWT (Keycloak)
- ou header signé depuis l'API Gateway / BFF

### B. Isolation BDD (preuve exigée)

Ibrahim doit implémenter ET PROUVER:

**PostgreSQL Row-Level Security (RLS)**:
- activée par défaut
- politique `tenant_isolation`

**Support**:
- mutualisé (shared tables + RLS)
- schéma dédié
- base dédiée

👉 Aucune requête SQL ne doit pouvoir contourner le RLS.

### C. CORE vs PLUGIN (rappel impératif)

- Si le microservice est désactivable sans casser l'ERP → PLUGIN
- S'il est indispensable au fonctionnement minimal → CORE

**Les plugins**:
- ne dépendent que du CORE
- ne dépendent jamais implicitement entre eux
- sont versionnés indépendamment

---

## 1.5 Manifest SaaS (OBLIGATOIRE pour chaque microservice)

Chaque microservice de Ibrahim doit exposer un manifest machine-lisible:

```json
{
  "module_id": "erp.accounting.ohada",
  "type": "plugin",
  "owner": "Ibrahim",
  "version": "1.0.0",
  "core_dependencies": ["erp.core"],
  "permissions": ["ACCOUNT_READ", "ACCOUNT_WRITE"],
  "migrations": ["V1__init.sql", "V2__rls.sql"],
  "tenant_modes": ["shared", "schema", "dedicated"],
  "activation_hooks": ["onEnable", "onDisable"],
  "billable": true
}
```

👉 Sans manifest conforme → refus catalogue SaaS.

---

## 1.6 CI / QA — Gates obligatoires

Aucun merge n'est autorisé si tous les tests suivants ne passent pas:
- tests unitaires
- tests d'intégration
- tests cross-tenant (A ≠ B)
- tests activation / désactivation plugin
- tests migration + rollback
- tests performance basiques (latence / charge)

---

## 1.7 Livrables exigés POUR CHAQUE Microservice

Avant de clôturer un microservice, Ibrahim doit fournir:

### 📄 Fiche d'identité du microservice
- nom
- rôle métier
- CORE ou PLUGIN
- dépendances

### 🧱 Schéma BDD actuel

### 🧭 Plan de transformation SaaS
- ce qui sort du CORE
- ce qui devient plugin

### 🧪 Plan de tests

### 📦 Manifest SaaS v1

### 📸 Preuves CI (logs / pipelines)

---

## Liste des microservices assignés à Ibrahim

D'après WAANGU_ERP_REVIEW.xlsx:

1. **erp-ms-tresorerie-backend** (Comptabilité générale - Plan comptable)
2. **asyst-ms-erp-comptabilite** (Écritures / Journaux / Grand Livre)
3. **erp-ms-comptabilite-analytic** (Axes analytiques / Allocations)
4. **waangu-gestion-commerciale-produit** (Gestion produits)
5. **waangu-gestion-commerciale-stock** (Gestion stock)
6. **waangu-gestion-commerciale-inventaire** (Inventaires)
7. **waangu-gestion-commerciale-parametage** (Paramétrages commerciaux)

---

## Équipe et responsabilités

### Backend Platform
- **George** (Lead Backend Platform Engineer): Starter Spring + contrats platform
- **Jackson** (SDK Engineer): SDK clients + validation manifest

### Backend Integration
- **Samuel** (Backend Integration Engineer): Engagement Hub + Payment Gateway

### QA & DevOps
- **Didier** (QA Automation Engineer): Suites de tests + preuves CI
- **Hugues** (DevOps): CI/CD, secrets, déploiement multi-région

### Infrastructure
- **Frantz** (Admin Réseau): Politiques réseau, segmentation

### Services Platform
- **George + Landry**: Traduction automatique
- **César**: Copilote hybride

### Leadership
- **Trésor** (CTO): Arbitrages CORE/PLUGIN, validation finale

---

## Contrats inter-équipes obligatoires

Chaque microservice d'Ibrahim doit:
1. **Dépendre du starter Platform** (George) - pas de duplication
2. **Respecter le contrat i18n** (George/Landry)
3. **Exposer des intents copilote** (César)
4. **Passer les gates QA** (Didier)
5. **Respecter les verrous DevOps/Network** (Hugues/Frantz)
6. **Documenter toute exception** (validation CTO Trésor)
