# Technology Stack

## Build System
- Maven (mvnw wrapper included)
- Java 21
- Spring Boot
 4.0.2

## Core Frameworks & Libraries

### Spring Boot Starters
- spring-boot-starter-web (REST controllers, filters)
- spring-boot-starter-jdbc (JdbcTemplate for RLS, audit, idempotency, outbox)
- spring-boot-starter-oauth2-resource-server (JWT validation)
- spring-boot-starter-aop (AspectJ for guards)

### Database
- PostgreSQL (runtime driver)
- Flyway (schema migrations)

### Utilities
- Jackson (JSON serialization for audit, outbox, idempotency)
- Commons Codec (SHA-256 for audit hash chain)
- Caffeine (caching for tenant registry and DataSource pooling)
- Lombok (code generation, optional)

### Testing
- spring-boot-starter-test

## Common Commands

### Build & Test
```bash
# Clean and build
./mvnw clean install

# Run tests
./mvnw test

# Package without tests
./mvnw package -DskipTests

# Run application
./mvnw spring-boot:run
```

### Maven Wrapper
Use `./mvnw` (Unix) or `mvnw.cmd` (Windows) instead of `mvn` to ensure consistent Maven version.

## Configuration
- Application config: `src/main/resources/application.yaml`
- Module ID configured via `waangu.module-id` property
