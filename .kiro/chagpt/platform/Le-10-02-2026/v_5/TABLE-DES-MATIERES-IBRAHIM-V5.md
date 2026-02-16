# Table des matières — Cahier des charges Ibrahim (Backend ERP)

**Référence officielle** : WAANGU_ERP_WITH_RESTO_LE_10_02_2026-V5.xlsx  
**Destinataire** : Ibrahim — Backend ERP Spring Boot  
**Règle** : Un chapitre / microservice à la fois ; le suivant ne démarre qu’une fois le précédent satisfaisant (DoD + garde-fous).

---

## Périmètre officiel (V5)

### Microservices sous ta responsabilité

| # | Microservice | Domaine principal | Criticité |
|---|--------------|-------------------|-----------|
| 1 | **erp-ms-tresorerie-backend** | Comptes trésorerie, flux financiers, mouvements, intégration Engagement Hub, rapprochements futurs | FinTech critique, paiements, double-spend impossible, idempotency, isolation multi-sociétés |
| 2 | **asyst-ms-erp-comptabilite** | Journaux, écritures, séquences, verrouillage périodes, immutabilité, export audit | Audit OPC, ISA/IFRS/IPSAS, hash chain, period lock irréversible, isolation légale |
| 3 | **erp-ms-comptabilite-analytic** | Axes analytiques, dimensions, règles d’allocation, exécution allocations, performance volumétrique | Cross-tenant, allocation cohérente, 10k+ lignes, idempotency run allocation |

### Ce que tu ne développes pas (intégration uniquement)

- Catalogue SaaS  
- Facturation SaaS  
- Onboarding tenant  
- Traduction automatique  
- Copilote IA  
- Payment Gateway engine interne  

---

## Garanties obligatoires (10 points — tous les MS)

1. Multi-tenant hybride  
2. Multi-sociétés  
3. Multi-pays  
4. Multi-langues (i18n ready)  
5. Isolation RLS stricte  
6. Immutabilité financière  
7. Idempotency  
8. Outbox pattern  
9. Audit trail hash chain  
10. CI hard guard  

**Aucun microservice ne doit passer en production sans ces 10 éléments.**

---

## Structure du cahier (ordre de travail)

---

### Partie 0 — Cadre & périmètre (V5)

| § | Titre | Contenu | Garde-fou |
|---|--------|--------|----------|
| 0.1 | Référence V5 & microservices Ibrahim | Liste officielle des 3 MS, domaines, criticité | Liste validée et partagée |
| 0.2 | Hors périmètre | Catalogue, facturation, onboarding, traduction, copilote, gateway engine | Tableau « ne pas développer » |
| 0.3 | Dix garanties systématiques | Les 10 points obligatoires par MS | Checklist 10/10 par MS |
| 0.4 | Risques & rappel ferme | Faille = danger conformité / OPC / fintech / ISO-SOC2 | Document rappel signé |

---

### Partie 1 — Ossature commune obligatoire

| § | Titre | Contenu | Garde-fou |
|---|--------|--------|----------|
| 1.1 | Structure repo (packages, dossiers) | config, controller, service, repository, domain, guard, audit, outbox, idempotency | Script vérification arborescence |
| 1.2 | Migrations SQL (convention) | V1 init, V2 RLS, V3 audit, V4 idempotency | Flyway validate + RLS check |
| 1.3 | CI (GitHub Actions) | ci.yml, jobs obligatoires, hard guard | Pipeline doit passer |
| 1.4 | Contrats & manifest | TENANT_CONTRACT, MIGRATION_ROLLBACK, manifest.json, manifest.schema.json | Fichiers présents + AJV |

---

### Partie 2 — erp-ms-tresorerie-backend (Chapitre détaillé)

| § | Titre | Contenu | Garde-fou |
|---|--------|--------|----------|
| 2.1 | Rôle métier & APIs | Comptes trésorerie, flux, mouvements, Engagement Hub, rapprochements | Spécification API |
| 2.2 | Modèle de données & RLS | Tables, tenant_id, legal_entity_id, policies | Test cross-tenant = 0 leak |
| 2.3 | Idempotency & double-spend | Clé idempotency, table, garde | Test idempotency obligatoire |
| 2.4 | Audit & outbox | Hash chain, outbox pour Engagement Hub | Audit log + outbox présents |
| 2.5 | Livrables & DoD | 5 livrables, checklist 10 garanties | Merge interdit si DoD non atteinte |

---

### Partie 3 — asyst-ms-erp-comptabilite (Chapitre détaillé)

| § | Titre | Contenu | Garde-fou |
|---|--------|--------|----------|
| 3.1 | Journaux, écritures, séquences | Modèle, APIs, séquençage atomique | Pas de trous de numérotation |
| 3.2 | Verrouillage périodes & immutabilité | Period lock, écritures non modifiables après post | Tests period lock + immutabilité |
| 3.3 | Export audit & hash chain | Trail immuable, OPC ready | Hash chain vérifié |
| 3.4 | Livrables & DoD | Idem Partie 2 | Idem |

---

### Partie 4 — erp-ms-comptabilite-analytic (Chapitre détaillé)

| § | Titre | Contenu | Garde-fou |
|---|--------|--------|----------|
| 4.1 | Axes, dimensions, règles d’allocation | Modèle, APIs | RLS + cross-tenant |
| 4.2 | Exécution allocations & performance | Run allocation, 10k+ lignes | Idempotency + perf test |
| 4.3 | Livrables & DoD | Idem Partie 2 | Idem |

---

### Partie 5 — Tableau de conformité V5 ↔ Ibrahim

| Critère V5 | MS#1 Trésorerie | MS#2 Compta | MS#3 Analytic |
|------------|-----------------|-------------|---------------|
| Multi-tenant hybride | ✔ | ✔ | ✔ |
| Multi-sociétés | ✔ | ✔ | ✔ |
| Multi-pays | ✔ | ✔ | ✔ |
| Multi-langues (i18n) | ✔ | ✔ | ✔ |
| RLS stricte | ✔ | ✔ | ✔ |
| Immutabilité financière | N/A (selon règles métier) | ✔ | ✔ (règles postées) |
| Idempotency | ✔ | ✔ | ✔ |
| Outbox pattern | ✔ | ✔ | ✔ |
| Audit trail hash chain | ✔ | ✔ | ✔ |
| CI hard guard | ✔ | ✔ | ✔ |

---

### Partie 6 — Annexes obligatoires

- TENANT_CONTRACT.md  
- MIGRATION_ROLLBACK.md  
- manifest.json  
- manifest.schema.json  
- (Selon plateforme : I18N_CONTRACT, COPILOT_CONTRACT, SECURITY_MODEL)

---

## Ordre de progression

1. **Valider Partie 0** (cadre V5, 10 garanties, rappel ferme).  
2. **Finaliser Partie 1** (ossature commune : structure repo, migrations, CI, contrats).  
3. **Traiter Partie 2** (erp-ms-tresorerie-backend) → DoD atteinte → clôture.  
4. **Traiter Partie 3** (asyst-ms-erp-comptabilite) → DoD atteinte → clôture.  
5. **Traiter Partie 4** (erp-ms-comptabilite-analytic) → DoD atteinte → clôture.  
6. **Remplir Partie 5** (tableau de conformité croisé V5 ↔ Ibrahim).  
7. **Maintenir Partie 6** (annexes à jour).

---

## Prochaine étape immédiate

- Soit : **attaquer erp-ms-tresorerie-backend** (implémentation complète production-grade).  
- Soit : **formaliser d’abord le tableau de conformité croisé V5 ↔ responsabilités Ibrahim** (Partie 5 détaillée).

Document de référence pour la suite : **IBRAHIM-CAHIER-V5-VERSION-FINALE.md** (détails, codes, scripts, garde-fous).
