# 20 questions pour partager le contexte en conversation (ChatGPT / autre)

**Objectif** : Copier-coller ces questions (ou un bloc) dans un **nouveau chat** pour que l’IA ait le même cadre que nos échanges : Waangu 360, multi-tenant, ERP, Ibrahim, méthode de gestion et sécurisation.

Tu peux :
- les envoyer **une par une** au début de la conversation, ou  
- coller **tout le bloc « Contexte en une fois »** (section 2) en premier message.

---

## 1. Les 20 questions (à poser ou à garder comme référence)

1. **Qu’est-ce que Waangu 360 et quel est son modèle SaaS ?**  
   *(Multi-tenant hybride, Odoo-like modulaire, 4 env, AWS Irlande/Virginie, DC par pays.)*

2. **Quelle est la règle CORE vs PLUGINS dans la Bible ERP ?**  
   *(Si désactivable sans casser l’ERP → PLUGIN ; sinon CORE. Plugins ne dépendent que du CORE.)*

3. **Quels sont les 3 microservices backend ERP officiellement sous la responsabilité d’Ibrahim (référence V5) ?**  
   *(erp-ms-tresorerie-backend, asyst-ms-erp-comptabilite, erp-ms-comptabilite-analytic.)*

4. **Que ne développe pas Ibrahim ?**  
   *(Catalogue SaaS, facturation SaaS, onboarding tenant, traduction automatique, copilote IA, moteur Payment Gateway — il s’intègre à ces services.)*

5. **Quels sont les 10 points que tout microservice doit garantir avant mise en production ?**  
   *(Multi-tenant hybride, multi-sociétés, multi-pays, multi-langues, RLS stricte, immutabilité financière, idempotency, outbox, audit trail hash chain, CI hard guard.)*

6. **Quels sont les 4 piliers obligatoires pour tout microservice Waangu 360 ?**  
   *(1 Plateforme/starter+contrats, 2 Données/tenant+RLS, 3 FinTech/audit, 4 CI/qualité.)*

7. **Qu’est-ce que le Tenant Registry et qui décide du mode ou du statut d’un tenant ?**  
   *(Source de vérité pour tenant_id, mode, billing_status, enabled_modules, routage BDD ; les microservices ne décident jamais, ils reçoivent le contexte via JWT ou Gateway.)*

8. **Quelles sont les 4 couches de sécurité entre la requête et la base de données ?**  
   *(1 JWT/Keycloak, 2 Filtre TenantContextFilter + SaaSContractGuard + ForbiddenBodyFields, 3 RLS + SET LOCAL, 4 Guards métier @FinancialEndpoint, immutabilité, period lock.)*

9. **Quels claims JWT sont obligatoires pour chaque requête et que faire s’ils manquent ?**  
   *(tenant_id, tenant_mode, legal_entity_id, country_code, locale, billing_status, enabled_modules, user_id, roles, correlation_id ; 401 si tenant_id ou contexte essentiel manquant.)*

10. **Pourquoi le RLS PostgreSQL est-il obligatoire et comment est-il utilisé ?**  
    *(Isolation garantie en BDD ; SET LOCAL app.current_tenant (et legal_entity) en début de transaction ; policy USING tenant_id = current_setting('app.current_tenant')::uuid sur toutes les tables métier.)*

11. **Quelles classes du starter plateforme ne doivent jamais être dupliquées dans un microservice ?**  
    *(TenantContextFilter, TenantRegistryClient, RoutingDataSource, DbSessionInitializer, IdempotencyService, AuditLogService, OutboxService, I18nClient, CopilotIntentController, ForbiddenBodyFieldsFilter, SaaSContractGuard.)*

12. **À quoi servent l’idempotency et l’outbox dans nos microservices financiers ?**  
    *(Idempotency : éviter double traitement / double-spend via Idempotency-Key et table idempotency_key. Outbox : publier les événements de façon fiable via table outbox_event dans la même transaction, worker publie ensuite.)*

13. **Comment doit être structuré l’audit trail pour être OPC / ISA ready ?**  
    *(Table audit_log append-only, avec prev_hash et curr_hash pour chaînage ; pas de UPDATE/DELETE ; tenant_id, legal_entity_id, actor_user_id, action, entity_type, entity_id, correlation_id, payload, occurred_at.)*

14. **Quels sont les jobs CI obligatoires et que doit faire le guard PR ?**  
    *(Jobs : unit, integration, cross_tenant, migrations, secrets_scan. Guard : vérifier checklist complète, preuves CI non vides, Migration/Rollback rempli si SQL modifié, jobs requis présents ; bloquer le merge si non conforme.)*

15. **Quelle est la structure de repo attendue pour un microservice backend ERP (packages Java, dossiers, fichiers racine) ?**  
    *(config, controller, service, repository, domain, guard, audit, outbox, idempotency ; db/migration V1–V4 ; .github/workflows/ci.yml ; TENANT_CONTRACT.md, MIGRATION_ROLLBACK.md, manifest.json, manifest.schema.json.)*

16. **Qu’est-ce qui doit bloquer le merge d’une PR et qu’est-ce qui doit bloquer la mise en production d’un microservice ?**  
    *(Merge : CI rouge, checklist incomplète, preuves vides, SQL sans Migration/Rollback, manifest invalide, table sans RLS, duplication starter. Prod : un des 10 points de garantie non satisfait, contrats ou plan migration/rollback absents.)*

17. **Comment onboarder un nouveau microservice pour qu’il soit conforme à la méthode Waangu 360 ?**  
    *(Checklist : starter utilisé, modèle avec tenant_id/legal_entity_id/country_code, migrations + RLS sur toutes les tables, tests cross_tenant + idempotency si besoin, contrats + manifest, CI + guard. Réutiliser l’ossature des MS existants.)*

18. **Quelles normes ou cadres réglementaires concernent nos microservices (audit, données, FinTech) ?**  
    *(ISA, ISQM/ISQC, OPC pour l’audit ; RGPD, data residency pour les données ; pas de logique PCI dans les MS — tout passe par le Payment Gateway ; ISO 27001, SOC 2 cibles plateforme.)*

19. **Quelle est la meilleure méthode pour gérer et sécuriser tous les microservices et tous les tenants ?**  
    *(Une seule ossature pour tous les MS ; un seul Tenant Registry ; sécurité en 4 couches ; checklist d’onboarding pour tout nouveau MS ; garde-fous automatiques en CI et en runtime ; aucun MS ni tenant en prod sans respect de la méthode.)*

20. **Où se trouve le référentiel détaillé (stratégie, Bible ERP, normes, specs par microservice, contrats, CI, méthode) ?**  
    *(Dans le dossier chagpt : 00 stratégie SaaS, 01 Bible ERP, 02 normes audit, 03–12 cahiers Ibrahim et récap ; METHODE-GESTION-SECURISATION-MICROSERVICES-TENANTS.md pour la méthode globale.)*

---

## 2. Contexte en une fois (à coller en premier message dans un nouveau chat)

Tu peux envoyer ce bloc tel quel pour « charger » tout le contexte d’un coup :

```
Contexte projet — Waangu 360 ERP SaaS

Nous travaillons sur Waangu 360, plateforme SaaS multi-tenant hybride (modèle Odoo-like), FinTech, avec 4 environnements (dev, test, préprod, prod), AWS Irlande/Virginie et DC par pays. La Bible ERP impose CORE vs PLUGINS : si un module est désactivable sans casser l’ERP, c’est un PLUGIN ; sinon CORE.

Backend ERP : 3 microservices sous la responsabilité d’Ibrahim (réf. V5) — erp-ms-tresorerie-backend, asyst-ms-erp-comptabilite, erp-ms-comptabilite-analytic. Ibrahim ne développe pas : catalogue SaaS, facturation, onboarding tenant, traduction automatique, copilote IA, ni le moteur Payment Gateway ; il s’intègre à ces services.

Chaque microservice doit garantir : (1) multi-tenant hybride, (2) multi-sociétés, (3) multi-pays, (4) multi-langues i18n, (5) isolation RLS stricte, (6) immutabilité financière, (7) idempotency, (8) outbox pattern, (9) audit trail hash chain, (10) CI hard guard. Quatre piliers : Plateforme (starter + contrats), Données (tenant_id + RLS), FinTech/audit (idempotency, outbox, hash chain), CI/qualité (jobs obligatoires + guard PR).

Tenant : un seul Tenant Registry (mode POOLED/SCHEMA/DEDICATED_DB, billing_status, enabled_modules). Les MS ne décident jamais du mode/statut ; contexte via JWT (Keycloak). Sécurité en 4 couches : JWT → TenantContextFilter + SaaSContractGuard + ForbiddenBodyFields → RLS (SET LOCAL + policies) → guards métier. Pas de duplication des classes du starter (TenantContextFilter, RoutingDataSource, DbSessionInitializer, IdempotencyService, AuditLogService, OutboxService, etc.). Référentiel détaillé : dossier chagpt (stratégie, Bible ERP, normes, specs Ibrahim, contrats, CI) et METHODE-GESTION-SECURISATION-MICROSERVICES-TENANTS.md.

Réponds en te basant sur ce cadre. Si tu as besoin de précision sur un point, demande.
```

---

## 3. Utilisation rapide

- **Nouveau chat** : colle d’abord le **bloc « Contexte en une fois »** (section 2), puis pose ta question métier ou technique.  
- **Vérifier que l’IA a le bon cadre** : pose 2–3 questions parmi la liste (ex. 5, 7, 19).  
- **Référence pour toi** : utilise la liste des 20 questions comme pense-bête des sujets à aligner avec l’IA en cas de doute.

Document créé pour réutiliser le contexte de nos échanges dans d’autres conversations (ChatGPT ou autre).
