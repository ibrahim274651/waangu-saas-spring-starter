# waangu-saas-spring-starter — Référence

**Emplacement** : `chagpt/waangu-saas-spring-starter/`  
**Artifact** : `com.waangu.platform:waangu-saas-spring-starter:1.0.0`

## Rôle

Bibliothèque Spring Boot unique pour le **multi-tenant hybride** Waangu 360. Tous les microservices backend ERP (Ibrahim et futurs) l’utilisent comme dépendance ; **aucune duplication** des classes du starter dans les microservices.

## Contenu (aligné chagpt)

- **tenant** : TenantContext, TenantContextHolder, TenantDbResolution, TenantRegistryClient, HttpTenantRegistryClient  
- **filter** : CorrelationIdFilter, TenantContextFilter, ForbiddenBodyFieldsFilter  
- **db** : DbSessionInitializer, RoutingDataSource  
- **annotation** : @FinancialEndpoint  
- **guard** : LegalEntityGuard  
- **audit** : AuditLogService (hash chain)  
- **idempotency** : IdempotencyService  
- **outbox** : OutboxService  
- **i18n** : I18nClient, HttpI18nClient  
- **copilot** : CopilotIntentController  
- **rbac** : Rbac  
- **autoconfig** : WaanguSaasAutoConfiguration  

## Utilisation dans un microservice

```xml
<dependency>
    <groupId>com.waangu.platform</groupId>
    <artifactId>waangu-saas-spring-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

```yaml
waangu:
  module-id: ERP_TREASURY_CORE
  tenant-registry-url: http://tenant-registry:8080
  translation-url: http://translation-service:8080
```

Dans chaque méthode `@Transactional` qui touche des données tenant : appeler `dbSessionInitializer.initForTx()` en première ligne.

## Build

Depuis `chagpt/waangu-saas-spring-starter` :

```bash
mvn clean install
```

Puis publier dans votre dépôt Maven (Artifactory, Nexus, GitHub Packages) pour que les microservices résolvent la dépendance.

## Voir aussi

- README dans `waangu-saas-spring-starter/README.md`
- METHODE-GESTION-SECURISATION-MICROSERVICES-TENANTS.md
- 04-IBRAHIM-MS1-TRESORERIE-SPECS.md, 08-IBRAHIM-MS1-CONTRACTS-DOCS.md, 12-RECAP-IBRAHIM-COMPLETE.md
