# Waangu SaaS Spring Starter - Configuration Guide

## Overview

This document describes all configuration properties available in the Waangu SaaS Spring Starter.

## Table of Contents

1. [Core Configuration](#core-configuration)
2. [Security Configuration](#security-configuration)
3. [Database Configuration](#database-configuration)
4. [Multi-Tenant Configuration](#multi-tenant-configuration)
5. [Platform Services](#platform-services)
6. [Logging & Monitoring](#logging--monitoring)

---

## Core Configuration

### Application Name
```yaml
spring:
  application:
    name: waangu-saas-spring-starter
```

### Module Identifier
```yaml
waangu:
  module-id: comptabilite  # or tresorerie, analytique, etc.
```

**Environment Variable**: `MODULE_ID`

---

## Security Configuration

### OAuth2 JWT Resource Server

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8089/realms/kwim-shop
          jwk-set-uri: http://localhost:8089/realms/kwim-shop/protocol/openid-connect/certs
```

**Environment Variables**:
- `KEYCLOAK_ISSUER_URI` - Keycloak realm issuer URI
- `KEYCLOAK_JWK_SET_URI` - JWK Set URI for token validation

### Security Headers

#### HSTS (HTTP Strict Transport Security)
```yaml
waangu:
  security:
    hsts:
      enabled: true
      max-age: 31536000  # 1 year in seconds
      include-subdomains: true
```

**Environment Variables**:
- `HSTS_ENABLED` - Enable/disable HSTS
- `HSTS_MAX_AGE` - Max age in seconds
- `HSTS_INCLUDE_SUBDOMAINS` - Include subdomains

#### CSP (Content Security Policy)
```yaml
waangu:
  security:
    csp:
      enabled: true
      policy: "default-src 'self'; script-src 'self' 'unsafe-inline'..."
```

**Environment Variables**:
- `CSP_ENABLED` - Enable/disable CSP
- `CSP_POLICY` - CSP policy string

#### CORS Configuration
```yaml
waangu:
  security:
    cors:
      enabled: true
      allowed-origins: http://localhost:3000,http://localhost:4200
      allowed-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
      allowed-headers: "*"
      exposed-headers: X-Correlation-Id,X-Tenant-Id
      allow-credentials: true
      max-age: 3600
```

**Environment Variables**:
- `CORS_ENABLED` - Enable/disable CORS
- `CORS_ALLOWED_ORIGINS` - Comma-separated list of allowed origins
- `CORS_ALLOWED_METHODS` - Comma-separated list of allowed methods
- `CORS_ALLOWED_HEADERS` - Comma-separated list of allowed headers
- `CORS_EXPOSED_HEADERS` - Comma-separated list of exposed headers
- `CORS_ALLOW_CREDENTIALS` - Allow credentials
- `CORS_MAX_AGE` - Preflight cache duration in seconds

---

## Database Configuration

### Primary DataSource

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/waangu_shared?sslmode=disable
    username: postgres
    password: postgres
    driver-class-name: org.postgresql.Driver
```

**Environment Variables**:
- `DB_URL` - JDBC URL
- `DB_USERNAME` - Database username
- `DB_PASSWORD` - Database password

### Connection Pool (HikariCP)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: WaanguHikariPool
```

**Environment Variables**:
- `DB_POOL_SIZE` - Maximum pool size
- `DB_POOL_MIN_IDLE` - Minimum idle connections
- `DB_CONNECTION_TIMEOUT` - Connection timeout (ms)
- `DB_IDLE_TIMEOUT` - Idle timeout (ms)
- `DB_MAX_LIFETIME` - Max connection lifetime (ms)

### Flyway Migrations

```yaml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 0
    locations: classpath:db/migration
```

**Environment Variables**:
- `FLYWAY_ENABLED` - Enable/disable Flyway
- `TENANT_ID` - Tenant ID placeholder for migrations

### RLS (Row Level Security)

```yaml
waangu:
  db-session:
    rls-enabled: true
    session-vars:
      app.current_tenant_id: ""
      app.current_user_id: ""
      app.correlation_id: ""
```

**Environment Variables**:
- `RLS_ENABLED` - Enable/disable RLS session initialization
- `SESSION_VAR_TENANT` - Default tenant ID
- `SESSION_VAR_USER` - Default user ID
- `SESSION_VAR_CORRELATION` - Default correlation ID

---

## Multi-Tenant Configuration

### Tenant Context Filter

```yaml
waangu:
  tenant-filter:
    enabled: false  # Set to true to enable
    registry-url: http://localhost:8080
    cache-ttl: 300  # 5 minutes
```

**Environment Variables**:
- `TENANT_FILTER_ENABLED` - Enable/disable tenant context extraction
- `TENANT_REGISTRY_URL` - Tenant registry service URL
- `TENANT_CACHE_TTL` - Cache TTL in seconds

**Important**: Set `enabled: true` in microservices that need multi-tenant support.

### Tenant Registry Client

The starter includes an HTTP client for the tenant registry service:

```java
@Autowired
private TenantRegistryClient tenantRegistryClient;

// Resolve database configuration for a tenant
TenantDbResolution resolution = tenantRegistryClient.resolveDatabase("tenant-001");
```

---

## Platform Services

### Audit Log Service

```yaml
waangu:
  audit:
    enabled: true
    table-name: platform_audit_logs
    hash-chain-enabled: true
    retention-days: 365
```

**Environment Variables**:
- `AUDIT_ENABLED` - Enable/disable audit logging
- `AUDIT_TABLE` - Audit log table name
- `AUDIT_HASH_CHAIN` - Enable hash chain for tamper detection
- `AUDIT_RETENTION_DAYS` - Retention period (0 = forever)

**Usage**:
```java
@Autowired
private AuditLogService auditLogService;

auditLogService.log(
    "USER_LOGIN",
    "User logged in successfully",
    Map.of("userId", userId, "ip", ipAddress)
);
```

### Idempotency Service

```yaml
waangu:
  idempotency:
    enabled: true
    table-name: platform_idempotency_keys
    ttl: 86400  # 24 hours
    header-name: Idempotency-Key
```

**Environment Variables**:
- `IDEMPOTENCY_ENABLED` - Enable/disable idempotency
- `IDEMPOTENCY_TABLE` - Idempotency keys table name
- `IDEMPOTENCY_TTL` - TTL in seconds
- `IDEMPOTENCY_HEADER` - HTTP header name

**Usage**:
```java
@Autowired
private IdempotencyService idempotencyService;

String idempotencyKey = request.getHeader("Idempotency-Key");
if (idempotencyService.isDuplicate(idempotencyKey)) {
    return idempotencyService.getResponse(idempotencyKey);
}

// Process request...
idempotencyService.store(idempotencyKey, response);
```

### Outbox Service

```yaml
waangu:
  outbox:
    enabled: true
    table-name: platform_outbox_events
    polling-interval: 5000  # 5 seconds
    batch-size: 100
    max-retries: 3
```

**Environment Variables**:
- `OUTBOX_ENABLED` - Enable/disable outbox pattern
- `OUTBOX_TABLE` - Outbox events table name
- `OUTBOX_POLLING_INTERVAL` - Polling interval (ms)
- `OUTBOX_BATCH_SIZE` - Batch size for processing
- `OUTBOX_MAX_RETRIES` - Max retry attempts

**Usage**:
```java
@Autowired
private OutboxService outboxService;

outboxService.publish(
    "ORDER_CREATED",
    "orders",
    orderId.toString(),
    orderData
);
```

### PII Guard

```yaml
waangu:
  pii-guard:
    enabled: true
    masked-fields: password,token,secret,apiKey,creditCard,ssn,taxId
    mask-char: "*"
```

**Environment Variables**:
- `PII_GUARD_ENABLED` - Enable/disable PII masking
- `PII_MASKED_FIELDS` - Comma-separated list of fields to mask
- `PII_MASK_CHAR` - Masking character

### Forbidden Fields Filter

```yaml
waangu:
  forbidden-fields:
    enabled: true
    fields: tenantId,tenant_id,createdBy,createdAt,updatedBy,updatedAt,deletedAt,version
```

**Environment Variables**:
- `FORBIDDEN_FIELDS_ENABLED` - Enable/disable forbidden fields filter
- `FORBIDDEN_FIELDS` - Comma-separated list of forbidden fields

### Correlation ID

```yaml
waangu:
  correlation:
    header-name: X-Correlation-Id
    generate-if-missing: true
```

**Environment Variables**:
- `CORRELATION_HEADER` - HTTP header name
- `CORRELATION_GENERATE` - Generate if missing

### Legal Entity Guard

```yaml
waangu:
  legal-entity-guard:
    enabled: true
    jwt-claim: legal_entity_id
```

**Environment Variables**:
- `LEGAL_ENTITY_GUARD_ENABLED` - Enable/disable legal entity guard
- `LEGAL_ENTITY_JWT_CLAIM` - JWT claim name for legal entity ID

---

## Logging & Monitoring

### Logging Levels

```yaml
logging:
  level:
    root: INFO
    com.waangu.platform: DEBUG
    org.springframework.security: INFO
    org.springframework.web: INFO
    org.hibernate: WARN
    org.flywaydb: INFO
```

**Environment Variables**:
- `LOG_LEVEL_ROOT` - Root logger level
- `LOG_LEVEL_WAANGU` - Waangu platform logger level
- `LOG_LEVEL_SECURITY` - Spring Security logger level
- `LOG_LEVEL_WEB` - Spring Web logger level
- `LOG_LEVEL_HIBERNATE` - Hibernate logger level
- `LOG_LEVEL_FLYWAY` - Flyway logger level

### Actuator Endpoints

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized
```

**Environment Variables**:
- `ACTUATOR_ENDPOINTS` - Comma-separated list of exposed endpoints
- `ACTUATOR_HEALTH_DETAILS` - Health details visibility

---

## Server Configuration

```yaml
server:
  port: 8080
  compression:
    enabled: true
  error:
    include-message: always
    include-binding-errors: always
    include-stacktrace: never
    include-exception: false
```

**Environment Variables**:
- `SERVER_PORT` - Server port
- `ERROR_INCLUDE_STACKTRACE` - Include stacktrace in error responses (never/always/on-param)

---

## Complete Example

### Development Environment

```yaml
spring:
  application:
    name: waangu-erp-comptabilite
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8089/realms/kwim-shop
  datasource:
    url: jdbc:postgresql://localhost:5432/waangu_erp_comptabilite
    username: postgres
    password: postgres

waangu:
  module-id: comptabilite
  tenant-filter:
    enabled: true
    registry-url: http://localhost:8080
  audit:
    enabled: true
  idempotency:
    enabled: true
  outbox:
    enabled: true

logging:
  level:
    com.waangu.platform: DEBUG
```

### Production Environment

```yaml
spring:
  application:
    name: waangu-erp-comptabilite
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.waangu.com/realms/production
  datasource:
    url: jdbc:postgresql://db.waangu.com:5432/waangu_erp_comptabilite?sslmode=require
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20

waangu:
  module-id: comptabilite
  tenant-filter:
    enabled: true
    registry-url: https://registry.waangu.com
    cache-ttl: 600
  security:
    hsts:
      enabled: true
    csp:
      enabled: true
    cors:
      allowed-origins: https://app.waangu.com
  audit:
    enabled: true
    retention-days: 2555  # 7 years
  idempotency:
    enabled: true
  outbox:
    enabled: true

logging:
  level:
    root: WARN
    com.waangu.platform: INFO
```

---

## Environment Variables Reference

| Variable | Default | Description |
|----------|---------|-------------|
| `MODULE_ID` | - | Module identifier |
| `KEYCLOAK_ISSUER_URI` | http://localhost:8089/realms/kwim-shop | Keycloak issuer URI |
| `DB_URL` | jdbc:postgresql://localhost:5432/waangu_shared | Database URL |
| `DB_USERNAME` | postgres | Database username |
| `DB_PASSWORD` | postgres | Database password |
| `TENANT_FILTER_ENABLED` | false | Enable tenant filter |
| `TENANT_REGISTRY_URL` | http://localhost:8080 | Tenant registry URL |
| `AUDIT_ENABLED` | true | Enable audit logging |
| `IDEMPOTENCY_ENABLED` | true | Enable idempotency |
| `OUTBOX_ENABLED` | true | Enable outbox pattern |
| `LOG_LEVEL_WAANGU` | DEBUG | Waangu logger level |

---

## Next Steps

1. Copy `application.yaml` to your microservice
2. Customize values for your environment
3. Set environment variables for sensitive data
4. Enable/disable features as needed
5. Review security settings for production

For more information, see the [README.md](README.md).
