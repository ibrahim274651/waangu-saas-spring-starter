# Project Structure

## Package Organization

Base package: `com.waangu.platform`

### Core Packages

- `annotation/` - Custom annotations (e.g., @FinancialEndpoint)
- `audit/` - Audit logging service with hash chain integrity
- `autoconfig/` - Spring Boot auto-configuration classes
- `copilot/` - AI copilot integration endpoints
- `db/` - Database routing and session initialization
  - RoutingDataSource: Routes queries to correct tenant database
  - DbSessionInitializer: Sets PostgreSQL session variables (RLS, tenant_id)
- `filter/` - Servlet filters (ordered by precedence)
  - CorrelationIdFilter: Ensures correlation ID on all requests
  - TenantContextFilter: Extracts and validates tenant context from JWT
  - ForbiddenBodyFieldsFilter: Blocks forbidden fields in request bodies
- `guard/` - AOP aspects for cross-cutting concerns
  - LegalEntityGuard: Enforces legal entity requirement on financial endpoints
- `i18n/` - Internationalization client interfaces
- `idempotency/` - Idempotency service for financial operations
- `outbox/` - Transactional outbox pattern implementation
- `rbac/` - Role-based access control utilities
- `tenant/` - Tenant context management
  - TenantContext: Immutable record holding tenant metadata
  - TenantContextHolder: ThreadLocal holder for tenant context
  - TenantRegistryClient: Fetches tenant configuration
  - TenantDbResolution: Resolves database connection for tenant mode

## Resource Structure

- `src/main/resources/`
  - `application.yaml` - Spring Boot configuration
  - `db/changelog/` - Liquibase changelogs (if used)
  - `db/migration/` - Flyway migration scripts

## Test Structure

- `src/test/java/com/waangu/platform/` - Mirror of main package structure

## Key Patterns

### Multi-Tenancy Flow
1. Request arrives → CorrelationIdFilter adds correlation ID
2. TenantContextFilter extracts JWT claims → validates tenant → populates TenantContext
3. DbSessionInitializer sets PostgreSQL session variables for RLS
4. RoutingDataSource routes to correct database based on tenant mode
5. Business logic executes with tenant isolation
6. Response returns → TenantContextHolder cleared

### Filter Order
- HIGHEST_PRECEDENCE: CorrelationIdFilter
- HIGHEST_PRECEDENCE + 10: TenantContextFilter
- HIGHEST_PRECEDENCE + 20: ForbiddenBodyFieldsFilter

### AOP Guards
Use `@Around` advice with custom annotations to enforce cross-cutting requirements (e.g., legal entity validation on financial endpoints).
