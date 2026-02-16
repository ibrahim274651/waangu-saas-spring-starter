# Code Standards & Best Practices

## Documentation

### JavaDoc Requirements
- All public classes, interfaces, and methods must have comprehensive JavaDoc
- Include `@param`, `@return`, `@throws` tags where applicable
- Provide usage examples for complex APIs using `<pre>{@code ...}</pre>` blocks
- Document thread-safety considerations and lifecycle expectations
- Cross-reference related classes using `{@link ClassName}`

### Comment Quality
- Remove placeholder comments (e.g., "chagpt", "TODO", "FIXME") before committing
- Comments should explain "why", not "what" (code should be self-documenting)
- Keep comments up-to-date with code changes

## Security

### SQL Injection Prevention
- NEVER use string concatenation for SQL queries
- Use parameterized queries with `JdbcTemplate.update(sql, params...)`
- For dynamic identifiers (schema names, table names), validate with regex: `^[a-zA-Z0-9_]+$`
- Example:
  ```java
  // BAD - SQL injection vulnerability
  jdbcTemplate.execute("SET LOCAL app.current_tenant = '" + tenantId + "'");
  
  // GOOD - parameterized query
  jdbcTemplate.update("SELECT set_config('app.current_tenant', ?, true)", tenantId);
  ```

### Input Validation
- Validate all external inputs (JWT claims, request parameters, headers)
- Use UUID validation for tenant IDs and entity IDs
- Enforce country code format (ISO 3166-1 alpha-2)
- Reject forbidden fields in request bodies (tenant_id, legal_entity_id, country_code)

### Tenant Isolation
- Always check `TenantContextHolder.get()` is not null before accessing tenant data
- Never trust client-provided tenant/legal entity values
- Use RLS (Row-Level Security) policies in PostgreSQL for defense in depth

## Code Quality

### Immutability
- Prefer Java records for data transfer objects (DTOs)
- Use `final` for fields that shouldn't change
- Return unmodifiable collections from public APIs

### Error Handling
- Use `ResponseStatusException` for HTTP errors with meaningful message keys
- Include correlation ID in error responses for traceability
- Log exceptions with appropriate severity levels
- Never expose internal implementation details in error messages

### Null Safety
- Use `Optional` for potentially absent values in return types
- Validate method parameters with null checks or `@NonNull` annotations
- Prefer empty collections over null for collection returns

### Resource Management
- Use try-with-resources for closeable resources
- Clear ThreadLocal variables in finally blocks
- Clean up MDC entries after request processing

## Testing

### Unit Tests
- Test happy path and error conditions
- Mock external dependencies (database, HTTP clients)
- Use meaningful test method names: `shouldRejectRequestWhenTenantIdMissing()`

### Integration Tests
- Test multi-tenant isolation
- Verify RLS policies work correctly
- Test filter ordering and interaction

## Naming Conventions

### Classes
- Services: `*Service` (e.g., `AuditLogService`)
- Filters: `*Filter` (e.g., `TenantContextFilter`)
- Guards: `*Guard` (e.g., `LegalEntityGuard`)
- Clients: `*Client` (e.g., `TenantRegistryClient`)
- Controllers: `*Controller` (e.g., `CopilotIntentController`)

### Methods
- Use verb-noun pattern: `initForTx()`, `requireLegalEntity()`, `withIdempotency()`
- Boolean methods: `is*()`, `has*()`, `can*()`
- Avoid generic names like `process()`, `handle()`, `execute()`

### Constants
- Use UPPER_SNAKE_CASE for constants
- Group related constants in dedicated classes or enums

## Performance

### Database Access
- Use connection pooling (HikariCP)
- Implement caching for tenant registry lookups (Caffeine)
- Batch database operations when possible
- Use appropriate transaction isolation levels

### Logging
- Use parameterized logging: `log.info("Processing tenant {}", tenantId)`
- Include correlation ID in all log statements (via MDC)
- Use appropriate log levels (ERROR, WARN, INFO, DEBUG, TRACE)
- Avoid logging sensitive data (passwords, tokens, PII)

## Multi-Tenancy Patterns

### Filter Order
Maintain strict filter ordering:
1. CorrelationIdFilter (HIGHEST_PRECEDENCE)
2. TenantContextFilter (HIGHEST_PRECEDENCE + 10)
3. ForbiddenBodyFieldsFilter (HIGHEST_PRECEDENCE + 20)

### Transaction Boundaries
- Call `DbSessionInitializer.initForTx()` at the start of each transaction
- Ensure TenantContext is available before database operations
- Use `@Transactional` for operations requiring atomicity

### Audit Trail
- Log all financial operations to audit_log table
- Include hash chain for tamper detection
- Store correlation ID for request tracing
- Never delete audit records (append-only)

## Dependency Management

### Version Consistency
- Use Spring Boot BOM for dependency management
- Keep all Spring Boot starters at the same version
- Document version upgrade considerations

### Optional Dependencies
- Mark Lombok as optional in pom.xml
- Configure annotation processors correctly
- Exclude optional dependencies from final artifact
