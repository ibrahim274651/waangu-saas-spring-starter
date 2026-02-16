# Chapitre 0 — Version finale (Partie 0 — Cadre général)

**Cahier des charges Ibrahim — ERP Backend Waangu 360**  
**Statut**: NORME OBLIGATOIRE — À valider avant toute Partie 1 à 9

---

## 0.1 Objet du cahier des charges

### 0.1.1 Énoncé

Ce cahier des charges a pour objet de **transformer les microservices et micro-frontends ERP existants** en composants **SaaS multi-tenant hybrides**, **multi-langues** et **multi-sociétés**, conformes à la **vision Waangu 360 FinTech**, dans le respect des **normes internationales et nationales** (audit, données, régulateurs).

### 0.1.2 Ce qui est transformé

- **Côté backend (périmètre Ibrahim)**: les **3 microservices ERP** listés en table des matières (trésorerie, comptabilité générale, comptabilité analytique).
- **Côté plateforme**: branchement contractuel aux services **Traduction automatique**, **Copilote hybride**, **Engagement Hub** et **Payment Gateway** (acquis) — pas de développement de ces briques par Ibrahim.

### 0.1.3 Ce qui ne change pas (contraintes)

- **CORE vs PLUGINS**: découpage BIBLE ERP inchangé ; CORE minimal, plugins optionnels.
- **Stack backend**: Spring Boot, PostgreSQL, RLS, Flyway, GitHub Actions.
- **Environnements**: dev, test, préprod, prod (AWS Irlande/Virginie, DC par pays si besoin).

### Garde-fou 0.1

- [ ] **GF-0.1** Le document « Objet » est rédigé et validé (1 page max).
- [ ] **GF-0.1b** Aucune nouvelle fonctionnalité métier hors transformation SaaS n’est introduite dans ce cahier sans accord explicite.

---

## 0.2 Périmètre exact d’Ibrahim

### 0.2.1 Microservices sous sa responsabilité (backend uniquement)

| # | Microservice | Rôle métier | Type |
|---|--------------|-------------|------|
| 1 | **erp-ms-tresorerie-backend** | Trésorerie, banques/caisses, rapprochements, flux financiers internes ERP | CORE |
| 2 | **asyst-ms-erp-comptabilite** | Comptabilité générale, journaux, écritures, verrouillage de périodes, préparation audit OPC | CORE |
| 3 | **erp-ms-comptabilite-analytic** | Axes analytiques, dimensions, règles d’allocation, postings analytiques | CORE |

**Technologies imposées pour ces 3 services**: Java 21, Spring Boot 3.x, JdbcTemplate (JPA interdit par défaut), PostgreSQL 16, RLS, Flyway, GitHub Actions.

### 0.2.2 Hors périmètre Ibrahim (répartition des tâches)

| Domaine | Responsable(s) | Rôle d’Ibrahim |
|---------|----------------|----------------|
| **Micro-frontends** | Cedric / Giscard (React Native Expo) | Exposer des APIs propres (REST, pagination, i18n par clé, erreurs normalisées). |
| **Traduction automatique** | George / Landry | Générer des **i18n keys** ; consommer l’API traduction ; **ne pas implémenter** le service. |
| **Copilote hybride** | César | Exposer des **intents métier** ; appliquer RBAC ; journaliser (audit). **Ne pas implémenter** le moteur IA. |
| **Engagement Hub & Payment Gateway** | Fournisseur acquis / Samuel (intégration) | **Intégrer via API** ; gérer idempotency, statuts, callbacks. **Aucune logique PCI** ; **aucun stockage sensible** côté Ibrahim. |

### 0.2.3 Règle de frontière

- **Ibrahim** ne modifie pas le code des micro-frontends ni des services Traduction / Copilote / Gateway.
- **Ibrahim** respecte les **contrats** (TENANT, I18N, COPILOT, Engagement Hub / Payment Gateway) définis dans les Parties 3 et 4.

### Garde-fou 0.2

- [ ] **GF-0.2** La liste officielle des 3 microservices est documentée et partagée.
- [ ] **GF-0.2b** Le tableau « Hors périmètre » est à jour avec les noms des responsables (Cedric, Giscard, George, Landry, César, Samuel).
- [ ] **GF-0.2c** Aucune tâche frontend, traduction moteur, copilote moteur ou logique PCI n’est assignée à Ibrahim.

---

## 0.3 Résultats attendus (ERP SaaS multi-tenant hybride)

### 0.3.1 Résultats par microservice

Pour **chaque** des 3 microservices, à l’issue de la transformation :

1. **Multi-tenant hybride**  
   - Fonctionnement dans les 3 modes : **mutualisé** (tables partagées + RLS), **schéma dédié** par tenant, **base dédiée** par tenant.
2. **Multi-sociétés**  
   - Données financières scopées par `legal_entity_id` ; guards et RLS en place.
3. **Multi-pays**  
   - `country_code` et paramètres localisables présents où nécessaire.
4. **Multi-langues**  
   - Libellés via **i18n keys** + API traduction (pas de texte métier en dur).
5. **Catalogue SaaS**  
   - Module **activable / désactivable** par tenant ; manifest valide (AJV) ; pas de rupture du CORE.
6. **Audit-ready (OPC)**  
   - Trail immuable, preuves, expositions read-only pour audit.
7. **FinTech-grade**  
   - Idempotency sur opérations critiques ; outbox pour événements ; immutabilité des écritures postées (MS#2, #3).

### 0.3.2 Livrables obligatoires par microservice (rappel)

1. **PR Spring** : starter utilisé, controllers/services/repos, guards (tenant, legal_entity, immutabilité si applicable).
2. **Migrations SQL** : tables avec tenant_id / legal_entity_id / country_code ; RLS ; audit_log / outbox_event / idempotency_key.
3. **Manifest** : manifest.json validé par manifest.schema.json (AJV en CI).
4. **CI green** : jobs unit, integration, cross_tenant, migrations (et secrets_scan) passants ; artefacts (logs, rapports).
5. **Documentation contracts** : TENANT_CONTRACT, I18N_CONTRACT, COPILOT_CONTRACT, SECURITY_MODEL, MIGRATION_ROLLBACK (référence ou fichier).

### 0.3.3 Definition of Done — Partie 0

La Partie 0 est **satisfaisante** si :

- Les sections 0.1 à 0.4 sont rédigées et validées.
- Les garde-fous 0.1, 0.2, 0.3, 0.4 sont cochés.
- La liste des 3 microservices et le hors périmètre sont figés et communiqués.
- Les résultats attendus (0.3.1) et les 5 livrables (0.3.2) sont acceptés comme référence pour les Parties 1 à 9.

### Garde-fou 0.3

- [ ] **GF-0.3** Les 7 résultats (multi-tenant, multi-sociétés, multi-pays, multi-langues, catalogue, audit OPC, FinTech) sont documentés.
- [ ] **GF-0.3b** Les 5 livrables par microservice sont listés et alignés avec la table des matières (Partie 7).
- [ ] **GF-0.3c** La DoD Partie 0 est signée (validation formelle) avant passage à la Partie 1.

---

## 0.4 Références réglementaires (FinTech, audit, données)

### 0.4.1 Audit & qualité

| Référence | Domaine | Exigence pour les MS Ibrahim |
|-----------|--------|-----------------------------|
| **ISA** (International Standards on Auditing) | Audit externe | Données fiables, traçables, exportables ; pas d’altération des écritures postées. |
| **ISQM / ISQC** | Qualité cabinets | Reproductibilité des contrôles ; trail immuable ; justification des écarts. |
| **OPC** (audit) | Preuves d’audit | Bilan, compte de résultat, grand livre, journaux, rapprochements, immobilisations, déclarations fiscales, contrats significatifs — import/API ERP. |

### 0.4.2 Données & conformité

| Référence | Domaine | Exigence pour les MS Ibrahim |
|-----------|--------|-----------------------------|
| **RGPD** | Données personnelles (UE) | Traitement licite ; isolation ; droits des personnes ; pas de stockage sensible hors cadre défini. |
| **Data residency** | Localisation | DC par pays si requis ; AWS Irlande/Virginie ; pas d’export non maîtrisé. |
| **PCI-DSS** (si paiements) | Cartes / paiements | **Aucune logique PCI** dans les microservices Ibrahim ; tout passe par le **Payment Gateway** acquis. |

### 0.4.3 Certifications cibles plateforme

- **ISO 27001** (sécurité de l’information).
- **SOC 2** (contrôles de service).
- Exigences fiscales locales (selon pays) — prises en charge par **plugins** (OHADA, IFRS, TVA, etc.), pas par le CORE des 3 MS.

### 0.4.4 Mapping court (à garder à jour)

| Norme / cadre | Où c’est traité dans le cahier |
|---------------|--------------------------------|
| ISA / OPC | Partie 5 (audit trail), Partie 7 (écritures, verrouillage périodes). |
| ISQM | Partie 5 (immuabilité, preuves). |
| RGPD / data residency | Partie 5 (conservation, localisation). |
| PCI | Partie 3 (Payment Gateway API only). |
| ISO 27001 / SOC 2 | Partie 5 (sécurité) ; hors détail certification dans ce cahier. |

### Garde-fou 0.4

- [ ] **GF-0.4** Les références ISA, ISQM, OPC, RGPD, data residency, PCI sont listées.
- [ ] **GF-0.4b** La règle « pas de logique PCI dans les MS Ibrahim » est explicite.
- [ ] **GF-0.4c** Le mapping norme → partie du cahier est à jour.

---

## Scripts et vérifications (Chapitre 0)

### Script 0 — Vérification de la liste des microservices (référentiel)

À exécuter en début de projet pour figer le périmètre (à adapter selon votre repo).

```bash
#!/bin/bash
# check_ibrahim_ms_list.sh
# Usage: ./check_ibrahim_ms_list.sh <repo_root>

REPO_ROOT="${1:-.}"
EXPECTED_MS=(
  "erp-ms-tresorerie-backend"
  "asyst-ms-erp-comptabilite"
  "erp-ms-comptabilite-analytic"
)

echo "Checking presence of Ibrahim microservice directories or configs..."
for ms in "${EXPECTED_MS[@]}"; do
  if [ -d "$REPO_ROOT/$ms" ] || [ -f "$REPO_ROOT/$ms/manifest.json" ] || grep -qR "$ms" "$REPO_ROOT" 2>/dev/null ; then
    echo "  [OK] $ms"
  else
    echo "  [WARN] $ms not found in $REPO_ROOT (may be in another repo)"
  fi
done
```

### Script 0b — Vérification des champs obligatoires (référentiel)

À utiliser en revue de modèle de données (exemple pour une table type).

```sql
-- Exemple: vérifier qu'une table métier a bien tenant_id, legal_entity_id, country_code
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_schema = 'public'
  AND table_name = 'treasury_bank_account'
  AND column_name IN ('tenant_id', 'legal_entity_id', 'country_code');
-- Attendu: 3 lignes, is_nullable = 'NO' pour les 3.
```

### Checklist de clôture Chapitre 0

- [ ] 0.1 Objet rédigé et validé (GF-0.1, GF-0.1b).
- [ ] 0.2 Périmètre figé : 3 MS listés, hors périmètre documenté (GF-0.2, GF-0.2b, GF-0.2c).
- [ ] 0.3 Résultats attendus et 5 livrables acceptés ; DoD Partie 0 signée (GF-0.3, GF-0.3b, GF-0.3c).
- [ ] 0.4 Références réglementaires et mapping à jour (GF-0.4, GF-0.4b, GF-0.4c).
- [ ] Script 0 (liste MS) exécuté et résultat conforme (ou N/A si autre organisation repo).
- [ ] Validation formelle (nom, date) : _________________________ Date : ___________

---

## Résumé exécutif

- **Objet** : Transformer les 3 microservices ERP backend d’Ibrahim en SaaS multi-tenant hybrides, multi-langues, multi-sociétés, Waangu 360 FinTech.
- **Périmètre Ibrahim** : erp-ms-tresorerie-backend, asyst-ms-erp-comptabilite, erp-ms-comptabilite-analytic. Hors périmètre : frontend, traduction (moteur), copilote (moteur), logique PCI ; intégration Engagement Hub / Payment Gateway par API uniquement.
- **Résultats attendus** : 3 modes tenant, multi-sociétés/pays/langues, catalogue activable/désactivable, audit OPC ready, FinTech-grade (idempotency, outbox, immutabilité). 5 livrables par MS (Spring, SQL, manifest, CI, docs).
- **Références** : ISA, ISQM, OPC, RGPD, data residency, PCI (non implémenté dans les MS), ISO 27001 / SOC 2 (cibles plateforme).

**Chapitre 0 est clos lorsque tous les garde-fous et la checklist de clôture sont satisfaits. On passe ensuite à la Partie 1 (Stack technique imposée).**
