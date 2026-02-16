# 🏛️ BIBLE ERP — CORE vs PLUGINS

**Waangu 360 — ERP SaaS Multi-Tenant Hybride**

**Statut**: NORME OBLIGATOIRE

Ce document définit ce qui appartient au CORE ERP et ce qui DOIT être implémenté comme PLUGIN SaaS.
Aucune interprétation locale n'est autorisée.
Toute déviation doit être documentée, justifiée et validée.

---

## 1) Objectif stratégique

Transformer l'ERP Waangu en un ERP SaaS modulaire "glisser-coller" comme Odoo, mais:
- multi-tenant hybride (mutualisé / dédié)
- audit-ready (banques, assurances, États)
- scalable globalement (Afrique, Europe, monde)
- certifiable (ISO 27001, SOC2, exigences fiscales locales)

👉 Le CORE est minimal, stable, universel.
👉 Les PLUGINS portent la complexité métier, sectorielle et locale.

---

## 2) Règle d'or absolue

**SI un module peut être désactivé chez un client sans casser l'ERP → C'EST UN PLUGIN.**
**SI un module est indispensable au fonctionnement minimal → C'EST DU CORE.**

---

## 3) ERP CORE — Définition (NON NÉGOCIABLE)

Le ERP CORE est:
- commun à 100% des clients
- toujours installé
- jamais optionnel
- extrêmement stable
- peu modifié dans le temps

### 3.1 Composants transverses du CORE ERP

#### A. Référentiels universels
- Tenant / Organisation
- Entités légales
- Utilisateurs & rôles (RBAC)
- Périodes fiscales & exercices
- Devises & taux de change
- Pays / fiscalités de base
- Langues
- Journaux techniques

#### B. Moteur comptable fondamental (Accounting Engine Core)

⚠️ **ATTENTION**: CORE ≠ COMPTABILITÉ COMPLÈTE

Le CORE contient le moteur, pas les règles locales.

**Inclus**:
- Journalisation en partie double
- Écritures comptables atomiques
- Plan comptable générique (template)
- Balance générale technique
- Numérotation & immutabilité des écritures
- Verrouillage des périodes

**❌ Exclu du CORE**:
- normes OHADA, SYSCOHADA, IFRS, GAAP
- TVA locale
- déclarations fiscales
👉 ce sont des PLUGINS

#### C. Moteur transactionnel commun
- Documents génériques (Document Engine)
- Workflow de validation générique
- États techniques (draft / validated / posted / archived)
- Audit trail natif

#### D. Noyau Stock minimal (Inventory Kernel)

**Inclus**:
- notion de produit
- unité de mesure
- mouvement de stock (in/out/transfer)
- stock théorique

**❌ Exclu**:
- multi-entrepôts complexes
- inventaires avancés
- traçabilité lot/série
👉 plugins

#### E. Noyau RH minimal (HR Kernel)

**Inclus**:
- Employé
- Contrat
- Département
- Rôles organisationnels

**❌ Exclu**:
- paie
- congés
- performance
- législation du travail
👉 plugins

---

## 4) ERP PLUGINS — Principe général

Un PLUGIN ERP est:
- activable / désactivable par tenant
- facturable séparément
- versionnable indépendamment
- optionnel
- localisable / sectorisable

Chaque plugin est un MODULE SaaS:
- backend + frontend
- manifest
- dépendances
- migrations
- permissions

---

## 5) Catalogue officiel — CORE vs PLUGINS par métier

### 5.1 COMPTABILITÉ

**CORE**:
- Accounting Engine
- Écritures
- Journaux
- Balance technique
- Exercices

**PLUGINS**:
- OHADA
- SYSCOHADA révisé
- IFRS
- GAAP
- TVA & taxes locales
- Déclarations fiscales
- Immobilisations
- Consolidation
- Reporting financier avancé
- Audit légal

### 5.2 STOCK / LOGISTIQUE

**CORE**:
- Produits
- Unités
- Mouvements simples
- Stock théorique

**PLUGINS**:
- Multi-entrepôts
- Inventaires
- Lots & numéros de série
- Traçabilité
- Valorisation FIFO/LIFO/CMUP
- Intégration douanes
- WMS avancé

### 5.3 ACHATS

**CORE**:
- Fournisseurs (référentiel)
- Bons de commande simples

**PLUGINS**:
- Demandes d'achat
- Appels d'offres
- Workflow multi-niveau
- Contrats fournisseurs
- Import/export
- Intégration e-procurement
- Gestion des engagements budgétaires

### 5.4 VENTES / FACTURATION

**CORE**:
- Clients (référentiel)
- Documents commerciaux génériques

**PLUGINS**:
- Devis
- Facturation avancée
- Abonnements
- Tarification complexe
- Remises
- Paiements
- Relances
- Intégration PSP / Mobile Money

### 5.5 RESSOURCES HUMAINES

**CORE**:
- Employé
- Contrat
- Organisation

**PLUGINS**:
- Paie (par pays)
- Congés
- Temps & présence
- Performance
- Formation
- Conformité droit du travail

### 5.6 TRÉSORERIE

**CORE**:
- Comptes génériques

**PLUGINS**:
- Banques
- Rapprochement bancaire
- Cash management
- Prévisions
- Multi-banques
- Intégration SWIFT/MT/Mobile Money

### 5.7 REPORTING & BI

**CORE**:
- Exports techniques
- API données

**PLUGINS**:
- Tableaux de bord métier
- BI financière
- États réglementaires
- Reporting sectoriel
- Export autorités

---

## 6) Règles techniques OBLIGATOIRES (CORE & PLUGINS)

### 6.1 Multi-tenant
- `tenant_id` obligatoire partout
- RLS PostgreSQL actif
- Support:
  - mutualisé
  - schéma dédié
  - base dédiée

### 6.2 Manifest module (OBLIGATOIRE)

Chaque plugin ERP doit exposer:
- `module_id`
- `version`
- `type` (core / plugin)
- `dépendances`
- `permissions`
- `migrations`
- `routes UI`
- `pricing hooks`

### 6.3 Dépendances
- Un plugin ne peut dépendre QUE du CORE
- Les plugins ne doivent pas dépendre entre eux sans déclaration explicite
- Cycles interdits

---

## 7) Facturation & activation

- **CORE**: toujours inclus
- **PLUGINS**: facturables
- Activation/désactivation par tenant
- Suspension possible sans casser le CORE
- Historique d'activation auditable

---

## 8) Audit & conformité

Tout plugin ERP doit fournir:
- logs tenant-scopés
- audit trail immuable
- exports autorités
- relecture complète des écritures
- preuve de non-altération

---

## 9) Tests obligatoires (gates CI)

- tests unitaires
- tests intégration
- tests cross-tenant
- tests activation/désactivation plugin
- tests migration/rollback
- tests performance

---

## 10) Critère final de conformité ERP

Un ERP Waangu est CONFORME si et seulement si:

1. Le CORE peut fonctionner seul
2. Chaque plugin peut être:
   - installé
   - activé
   - désactivé
   - facturé
   - audité
3. Aucun plugin ne compromet:
   - l'isolation tenant
   - la conformité réglementaire
   - la stabilité du CORE

---

## 11) Règle de clôture

**SI un module ERP est indispensable → il n'a PAS sa place en plugin.**
**SI un module est spécifique, local, sectoriel ou optionnel → il DOIT être un plugin.**
