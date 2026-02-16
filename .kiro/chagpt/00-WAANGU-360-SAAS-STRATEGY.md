# Waangu 360 - Multi-Tenant SaaS Strategy

## 1. Introduction au modèle SaaS modulaire Waangu 360

L'architecture multi-tenant permet à une seule instance logicielle de servir plusieurs clients (ou « tenants ») distincts, tout en isolant les données de chacun. La plateforme Waangu 360 reprend cette logique : inspirée du modèle modulaire d'Odoo, elle décompose l'application en modules indépendants (microservices pour le back-end, micro-frontends pour le front-end) qui peuvent être activés ou désactivés par client.

## 2. Environnements

- **4 environnements**: dev, test, pré-prod et production
- **Régions cloud**: 
  - AWS Irlande (primaire)
  - AWS Virginie (secondaire)
  - DC par pays (conformité régulateurs nationaux)

## 3. Modèles multi-tenant

### Base partagée, schéma partagé (shared-everything)
- Tous les clients partagent la même base de données et les mêmes tables
- Identifiés par une clé `tenant_id`
- **Avantages**: simplicité de déploiement et de mise à jour, coûts réduits
- **Inconvénients**: faible isolation, risque de "noisy neighbors"

### Base partagée, schémas séparés
- Une seule base de données physique
- Chaque client a son propre schéma (ex. PostgreSQL schemas)
- **Avantages**: meilleure séparation logique
- **Inconvénients**: maintenance plus complexe

### Base dédiée par client
- Chaque client a sa propre instance de base
- **Avantages**: isolation maximale, personnalisation totale
- **Inconvénients**: forte complexité opérationnelle, coûts élevés

## 4. Modèle hybride hiérarchique Waangu 360

**Hiérarchie**: pays → tenant → sous-tenant → module

### Avantages
- Hébergement massif des clients standards sur infrastructure mutualisée
- Environnements isolés pour clients à forts enjeux
- Transition tenant entre bases partagées et dédiées selon besoins

### Critères de migration vers dédié
1. Exigences de conformité (finance, santé)
2. Volume de données ou trafic critique
3. SLA très élevé ou contrôle total requis

## 5. Implémentation technique

### Schéma conceptuel (ERD)
Entités centrales:
- **Tenant**: tenant_id, nom, pays, parent_id (hiérarchie)
- **Utilisateur**: avec rôle et clé étrangère vers Tenant
- **Module**: liste des modules actifs par tenant
- **Entitlement/Feature Flag**: paramètres spécifiques par tenant

### Provisioning
- Onboarding automatisé via API d'administration
- Création de schéma ou base de données
- Initialisation des données de référence
- Tenant metadata registry (catalogue central)

### Service Discovery
- Microservices sur Kubernetes (EKS/AKS)
- Micro-frontends via CDN
- Routage basé sur tenant (sous-domaine ou token JWT)

### RLS et isolement
- **Row-Level Security PostgreSQL**
- Politique: `WHERE tenant_id = current_setting('app.current_tenant')`
- Schéma dédié par tenant pour forte exigence

### Autorisation (RBAC)
- Rôles granulaires par module et par tenant
- Service d'annuaire central (Keycloak ou AWS Cognito)
- Vérification systématique avant action métier

## 6. Gouvernance et sécurité

### RBAC et gouvernance
- Contrôle d'accès par rôles strict
- Administrateurs par tenant
- Audits d'accès systématiques

### Isolation des données et chiffrement
- Chiffrement au repos (base/schéma)
- Chiffrement PII au niveau applicatif (AES)
- TLS pour toutes communications

### Surveillance et conformité
- SIEM (CloudTrail/Azure Monitor)
- Alertes sur comportements anormaux
- Certifications: ISO 27001, SOC 2

## 7. Déploiement cloud

### AWS
- **Avantages**: scalabilité, services managés, certifications
- **Inconvénients**: coût élevé

### Azure
- **Avantages**: intégration Microsoft, régions gouvernementales
- **Inconvénients**: coûts comparables AWS

### Hetzner
- **Avantages**: tarif inférieur, certifié ISO 27001, GDPR-friendly
- **Inconvénients**: pas de régions hors Europe, moins de services managés

## 8. Cas d'usage par secteur

### PME/TPE
- Modèle mutualisé standard
- Fonctionnalités prêtes à l'emploi
- Accent sur simplicité d'usage

### Gouvernement / secteur public
- Exigences sécurité maximales
- Data centers spécifiés
- Mode dédié pour chaque entité
- Audits réglementaires périodiques

### FinTech
- Conformité PCI-DSS
- Traçabilité totale
- Base dédiée préférée
- Génération automatique de rapports d'audit

### Entreprise multisite
- Tenants hiérarchiques (siège + filiales)
- Consolidation globale + isolation locale
- Multi-warehouse et multi-magasin
