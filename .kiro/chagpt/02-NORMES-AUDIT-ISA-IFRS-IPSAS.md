# Cadre Normatif Officiel — ERP Comptabilité & Audit-Ready

## Normes Applicables

| Norme / Cadre | Description |
|--------------|-------------|
| **ISA** – International Standards on Auditing (IFAC) | Normes internationales d'audit & certification |
| **IFRS** – International Financial Reporting Standards | Référentiel comptable international |
| **IPSAS** – Pour les entités publiques | Normes comptables du secteur public |
| **ISQC / ISQM** – Contrôle qualité des cabinets | Assurance qualité interne des cabinets |

---

## 1️⃣ ISA — International Standards on Auditing

### Rôle dans Waangu 360
Les ISA sont la référence d'audit et de certification.
👉 Elles ne modifient jamais la comptabilité, elles l'analysent.

### Exigences techniques imposées au CORE comptable
- États financiers reproductibles à date donnée
- Journaux et grand livre immutables
- Historique complet des corrections (contre-passations)
- APIs read-only dédiées à l'audit
- Liens écriture ↔ pièce justificative

### Documents indispensables à collecter (OPC)
- Bilan + Compte de résultat + Annexes
- Grand livre + Journaux (achats, ventes, banques, caisse)
- États de rapprochements bancaires
- Fichiers immobilisations + amortissements
- Inventaires + stocks + assurances
- Déclarations fiscales (TVA / IS / Taxes locales)
- Contrats commerciaux significatifs
- Procédures internes & manuels comptables

### Moteur d'Audit Normalisé (Checklists Automatisées)

Chaque norme ISA devient un workflow:

#### ISA 200: Objectifs généraux
- Introduction audit

#### ISA 300: Planification
- Checklist initiale

#### ISA 315: Évaluation des risques
- Scoring automatique

#### ISA 330: Réponses aux risques
- Mesures & vérifications

#### ISA 500: Preuves d'audit
- Pièces justificatives

#### ISA 700: Formation opinion
- Génération rapport

---

## 2️⃣ IFRS — International Financial Reporting Standards

### Rôle dans Waangu 360
IFRS = référentiel comptable, pas un moteur.

### Règle d'architecture ABSOLUE
- ❌ IFRS n'est JAMAIS dans le CORE
- ✅ IFRS = PLUGIN comptable au-dessus du CORE

### Responsabilité développeurs
Le CORE doit:
- fournir un Accounting Engine neutre
- exposer des hooks / APIs / events
- permettre à un plugin IFRS de:
  - retraiter les écritures
  - produire bilan / P&L IFRS
  - générer annexes IFRS

---

## 3️⃣ IPSAS — Normes comptables du secteur public

### Rôle dans Waangu 360
IPSAS s'applique aux:
- États
- collectivités
- agences publiques
- institutions parapubliques

### Décision structurante
**IPSAS = PLUGIN sectoriel**
- activé uniquement pour tenants publics

### Exigences pour le CORE
Le CORE doit déjà gérer:
- multi-entités légales
- budgets vs réalisés
- exercices non commerciaux
- traçabilité renforcée (secteur public)

---

## 4️⃣ ISQC / ISQM — Contrôle qualité des cabinets d'audit

### Rôle dans Waangu 360
Ces normes concernent:
- la qualité du travail d'audit
- la traçabilité des décisions
- la reproductibilité des contrôles

### Impact indirect MAIS CRITIQUE pour le CORE
Le CORE doit permettre:
- relecture complète d'un exercice
- reproduction exacte d'un audit à date passée
- justification automatique des écarts
- conservation longue durée des preuves

---

## 5️⃣ Tableau de synthèse

| Norme | Où elle vit | Impact sur le CORE comptable |
|-------|-------------|------------------------------|
| **ISA** | Plugin Audit (OPC) | Données fiables, traçables, exportables |
| **IFRS** | Plugin comptable | Neutralité du CORE, hooks propres |
| **IPSAS** | Plugin public | Multi-entités, budgets, audit public |
| **ISQM** | Processus audit | Immutabilité, explicabilité, historisation |

---

## 6️⃣ Traduction en exigences techniques concrètes

### Données
- écritures atomiques
- journalisation complète
- horodatage précis
- aucune suppression physique

### APIs
- APIs comptables (CRUD contrôlé)
- APIs audit READ ONLY
- APIs d'export normées (CSV / JSON / PDF)

### Sécurité & conformité
- RLS PostgreSQL obligatoire
- logs tenant-scopés
- hash / checksum des pièces
- preuve d'intégrité

### Extensibilité
- événements normalisés
- aucune dépendance circulaire
- aucun hard-coding de normes

---

## 7️⃣ Message aux développeurs

Vous ne codez pas une comptabilité locale.
Vous codez un socle financier universel, capable de supporter:
- IFRS (international)
- IPSAS (États)
- ISA (audit)
- ISQM (qualité Big-4)

**Si une norme peut être désactivée → ce n'est PAS du CORE.**
**Si l'audit ne peut pas s'appuyer sur vos données → votre CORE est invalide.**
