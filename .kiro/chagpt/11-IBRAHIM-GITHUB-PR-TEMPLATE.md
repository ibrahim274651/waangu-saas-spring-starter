# Pull Request Template — Ibrahim ERP Backend

## 📋 Summary
<!-- Brief description of changes (1-2 sentences) -->


---

## 🎯 Microservice(s) Affected
<!-- Check all that apply -->
- [ ] #1 erp-ms-tresorerie-backend
- [ ] #2 asyst-ms-erp-comptabilite
- [ ] #3 erp-ms-comptabilite-analytic
- [ ] Other: _____________

---

## 🔗 CI Pipeline Links (REQUIRED — must be non-empty)

**CI_PIPELINE_LINK**: <REQUIRED>

**CI_UNIT_JOB_LINK**: <REQUIRED>

**CI_INTEGRATION_JOB_LINK**: <REQUIRED>

**CI_CROSS_TENANT_JOB_LINK**: <REQUIRED>

**CI_MIGRATIONS_JOB_LINK**: <REQUIRED>

**CI_TEST_LOGS_ARTEFACTS_LINK**: <REQUIRED>

---

## 📦 Migration / Rollback Plan

**MIGRATION_PLAN**: <REQUIRED IF SQL CHANGED>

**ROLLBACK_PLAN**: <REQUIRED IF SQL CHANGED>

**SQL_FILES_CHANGED**: 
- [ ] Yes (fill above) 
- [ ] No

---

## ✅ Checklist Obligatoire (20/20 requis)

### Multi-Tenant Compliance
- [ ] 1. `tenant_id` présent dans toutes nouvelles tables métier
- [ ] 2. `legal_entity_id` présent dans toutes tables financières
- [ ] 3. RLS activé et testé sur toutes tables tenant-scopées
- [ ] 4. Test cross-tenant passé (Tenant A ≠ Tenant B = 0 leak)

### Multi-Country / Multi-Company / Multi-Language
- [ ] 5. `country_code` présent dans tables nécessitant localisation
- [ ] 6. `legal_entity_id` obligatoire sur endpoints financiers (@FinancialEndpoint)
- [ ] 7. i18n: `*_i18n_key` + `*_source` pour tous labels métier
- [ ] 8. Integration Translation Service testée (ou mocked)

### Platform Starter (George)
- [ ] 9. Dépendance `waangu-saas-spring-starter` ajoutée (pas de duplication classes)
- [ ] 10. TenantContextFilter utilisé (pas réimplémenté)
- [ ] 11. DbSessionInitializer utilisé (SET LOCAL dans transactions)
- [ ] 12. RoutingDataSource utilisé (modes hybrides)

### Copilot (César)
- [ ] 13. Endpoint `/copilot/intents` exposé et documenté
- [ ] 14. Intents déclarent rôles RBAC requis

### Audit & Immutabilité
- [ ] 15. audit_log écrit pour toutes mutations critiques
- [ ] 16. Hash chain (prev_hash/curr_hash) implémenté si applicable
- [ ] 17. Immutabilité `posted_at != null` gardée (409 si modif tentée)

### Idempotency & Outbox
- [ ] 18. `Idempotency-Key` requis sur endpoints POST critiques
- [ ] 19. Outbox pattern utilisé pour events externes (pas d'appels directs)

### CI & Manifest
- [ ] 20. Manifest validé par AJV (schema JSON) + tous jobs CI green

---

## 🧪 Tests Exécutés
<!-- Cochez les tests exécutés localement avant PR -->
- [ ] Unit tests
- [ ] Integration tests
- [ ] Cross-tenant RLS test
- [ ] Idempotency test
- [ ] Immutability test (si applicable)
- [ ] Period lock test (si MS#2)
- [ ] Performance test (optionnel)

---

## 📝 Notes Additionnelles
<!-- Tout contexte additionnel, décisions architecturales, trade-offs -->


---

## 🚨 Breaking Changes
<!-- Y a-t-il des breaking changes API / DB schema ? -->
- [ ] Oui (expliquer ci-dessous)
- [ ] Non

**Explication**: 


---

## 👥 Reviewers Requis
<!-- Auto-assigné via CODEOWNERS, mais rappel ici -->
- Platform: @george-platform
- QA: @didier-qa
- DevOps: @hugues-devops
- CTO (si CORE change): @tresor-cto

---

## ✍️ Signature Développeur
Je confirme avoir:
- Testé localement tous les scénarios
- Vérifié qu'aucune classe Platform n'est dupliquée
- Rempli tous les champs REQUIRED ci-dessus
- Exécuté les tests cross-tenant avec succès

**Nom**: Ibrahim  
**Date**: YYYY-MM-DD
