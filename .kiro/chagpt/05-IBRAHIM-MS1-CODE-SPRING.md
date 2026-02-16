# Code Implementation — Microservice #1 (erp-ms-tresorerie-backend)

**Stack**: Spring Boot 3.x + Java 21 + PostgreSQL 16 + JdbcTemplate

---

## Project Structure

```
src/main/java/com/waangu/erp/treasury/
├── api/
│   ├── BankAccountController.java
│   ├── BankStatementController.java
│   ├── ReconciliationController.java
│   └── dto/
│       ├── BankAccountCreateRequest.java
│       ├── BankAccountResponse.java
│       ├── ReconciliationRunRequest.java
│       └── ...
├── service/
│   ├── BankAccountService.java
│   ├── BankStatementService.java
│   ├── ReconciliationService.java
│   └── ...
├── repo/
│   ├── BankAccountRepository.java
│   ├── BankStatementRepository.java
│   └── ...
└── config/
    └── TreasuryModuleConfig.java
```

---

## 1) DTOs

### BankAccountCreateRequest.java
```java
package com.waangu.erp.treasury.api.dto;

public record BankAccountCreateRequest(
    String currencyCode,
    String bankName,
    String iban,
    String accountNumber,
    String nameSource  // will generate i18n key
) {
    // NO tenant_id, NO legal_entity_id - comes from JWT!
}
```

### BankAccountResponse.java
```java
package com.waangu.erp.treasury.api.dto;

import java.util.UUID;

public record BankAccountResponse(
    UUID id,
    String currencyCode,
    String bankName,
    String iban,
    String accountNumber,
    String nameI18nKey,
    String nameSource
) {}
```

### ReconciliationRunRequest.java
```java
package com.waangu.erp.treasury.api.dto;

import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationRunRequest(
    UUID bankAccountId,
    LocalDate fromDate,
    LocalDate toDate
) {}
```

---

## 2) Controllers

### BankAccountController.java
```java
package com.waangu.erp.treasury.api;

import com.waangu.erp.treasury.api.dto.*;
import com.waangu.erp.treasury.service.BankAccountService;
import com.waangu.platform.annotation.FinancialEndpoint;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/erp/treasury/bank-accounts")
@FinancialEndpoint  // From starter: requires legal_entity_id
public class BankAccountController {

    private final BankAccountService service;

    public BankAccountController(BankAccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<BankAccountResponse> list() {
        return service.list();
    }

    @PostMapping
    public BankAccountResponse create(
            @RequestBody BankAccountCreateRequest req,
            @RequestHeader("Idempotency-Key") String idemKey) {
        return service.create(req, idemKey);
    }

    @GetMapping("/{id}")
    public BankAccountResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public BankAccountResponse update(
            @PathVariable UUID id,
            @RequestBody BankAccountCreateRequest req,
            @RequestHeader(value = "If-Match", required = true) Long version) {
        return service.update(id, req, version);
    }
}
```

### ReconciliationController.java
```java
package com.waangu.erp.treasury.api;

import com.waangu.erp.treasury.api.dto.ReconciliationRunRequest;
import com.waangu.erp.treasury.service.ReconciliationService;
import com.waangu.platform.annotation.FinancialEndpoint;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/erp/treasury/reconciliations")
@FinancialEndpoint
public class ReconciliationController {

    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    @PostMapping("/run")
    public Map<String, Object> run(
            @RequestBody ReconciliationRunRequest req,
            @RequestHeader("Idempotency-Key") String idemKey) {
        return service.run(req, idemKey);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable UUID id) {
        return service.get(id);
    }
}
```

---

## 3) Services

### BankAccountService.java
```java
package com.waangu.erp.treasury.service;

import com.waangu.erp.treasury.api.dto.*;
import com.waangu.erp.treasury.repo.BankAccountRepository;
import com.waangu.platform.service.*;
import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class BankAccountService {

    private final BankAccountRepository repo;
    private final I18nClient i18n;
    private final AuditLogService audit;
    private final OutboxService outbox;
    private final IdempotencyService idempo;
    private final DbSessionInitializer dbInit;

    public BankAccountService(
            BankAccountRepository repo,
            I18nClient i18n,
            AuditLogService audit,
            OutboxService outbox,
            IdempotencyService idempo,
            DbSessionInitializer dbInit) {
        this.repo = repo;
        this.i18n = i18n;
        this.audit = audit;
        this.outbox = outbox;
        this.idempo = idempo;
        this.dbInit = dbInit;
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> list() {
        dbInit.initForTx();
        return repo.list();
    }

    @Transactional(readOnly = true)
    public BankAccountResponse get(UUID id) {
        dbInit.initForTx();
        return repo.get(id);
    }

    @Transactional
    public BankAccountResponse create(BankAccountCreateRequest req, String idemKey) {
        dbInit.initForTx();

        String reqHash = Hashing.sha256(
            req.currencyCode() + "|" +
            req.bankName() + "|" +
            req.iban() + "|" +
            req.accountNumber() + "|" +
            req.nameSource()
        );

        return idempo.withIdempotency(idemKey, reqHash, () -> {
            TenantContext ctx = TenantContextHolder.get();

            UUID id = UUID.randomUUID();
            String i18nKey = "erp.treasury.bank_account." + id + ".name";

            // Register translation
            i18n.upsertSource(i18nKey, ctx.locale(), req.nameSource());

            // Insert
            BankAccountResponse created = repo.insert(id, req, i18nKey);

            // Audit
            audit.write(
                "TREASURY_BANK_ACCOUNT_CREATED",
                "treasury_bank_account",
                id,
                Map.of(
                    "iban", req.iban(),
                    "currency", req.currencyCode(),
                    "bankName", req.bankName()
                )
            );

            // Outbox
            outbox.emit("TREASURY.BANK_ACCOUNT.CREATED", Map.of(
                "id", id.toString(),
                "iban", req.iban()
            ));

            return created;
        });
    }

    @Transactional
    public BankAccountResponse update(UUID id, BankAccountCreateRequest req, Long version) {
        dbInit.initForTx();

        BankAccountResponse existing = repo.get(id);

        // Check immutability
        if (existing.postedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "IMMUTABLE_POSTED");
        }

        // Optimistic locking
        int updated = repo.update(id, req, version);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION");
        }

        audit.write("TREASURY_BANK_ACCOUNT_UPDATED", "treasury_bank_account", id,
            Map.of("iban", req.iban()));

        outbox.emit("TREASURY.BANK_ACCOUNT.UPDATED", Map.of("id", id.toString()));

        return repo.get(id);
    }
}
```

---

## 4) Repositories

### BankAccountRepository.java
```java
package com.waangu.erp.treasury.repo;

import com.waangu.erp.treasury.api.dto.*;
import com.waangu.platform.tenant.TenantContext;
import com.waangu.platform.tenant.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

@Repository
public class BankAccountRepository {

    private final JdbcTemplate jdbc;

    public BankAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<BankAccountResponse> list() {
        return jdbc.query("""
            SELECT id, currency_code, bank_name, iban, account_number,
                   name_i18n_key, name_source
            FROM treasury_bank_account
            WHERE is_active = true
            ORDER BY created_at DESC
        """, (rs, i) -> new BankAccountResponse(
            UUID.fromString(rs.getString("id")),
            rs.getString("currency_code"),
            rs.getString("bank_name"),
            rs.getString("iban"),
            rs.getString("account_number"),
            rs.getString("name_i18n_key"),
            rs.getString("name_source")
        ));
    }

    public BankAccountResponse get(UUID id) {
        List<BankAccountResponse> results = jdbc.query("""
            SELECT id, currency_code, bank_name, iban, account_number,
                   name_i18n_key, name_source
            FROM treasury_bank_account
            WHERE id = ?
        """, (rs, i) -> new BankAccountResponse(
            UUID.fromString(rs.getString("id")),
            rs.getString("currency_code"),
            rs.getString("bank_name"),
            rs.getString("iban"),
            rs.getString("account_number"),
            rs.getString("name_i18n_key"),
            rs.getString("name_source")
        ), id);

        if (results.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "BANK_ACCOUNT_NOT_FOUND");
        }
        return results.get(0);
    }

    public BankAccountResponse insert(UUID id, BankAccountCreateRequest req, String nameI18nKey) {
        TenantContext ctx = TenantContextHolder.get();

        jdbc.update("""
            INSERT INTO treasury_bank_account(
                id, tenant_id, legal_entity_id, country_code,
                currency_code, bank_name, iban, account_number,
                name_i18n_key, name_source, created_by
            ) VALUES (?,?,?,?,?,?,?,?,?,?,?)
        """,
            id,
            UUID.fromString(ctx.tenantId()),
            UUID.fromString(ctx.legalEntityId()),
            ctx.countryCode(),
            req.currencyCode(),
            req.bankName(),
            req.iban(),
            req.accountNumber(),
            nameI18nKey,
            req.nameSource(),
            UUID.fromString(ctx.userId())
        );

        return new BankAccountResponse(
            id,
            req.currencyCode(),
            req.bankName(),
            req.iban(),
            req.accountNumber(),
            nameI18nKey,
            req.nameSource()
        );
    }

    public int update(UUID id, BankAccountCreateRequest req, Long version) {
        TenantContext ctx = TenantContextHolder.get();

        return jdbc.update("""
            UPDATE treasury_bank_account
            SET bank_name = ?,
                iban = ?,
                account_number = ?,
                version = version + 1,
                updated_at = now(),
                updated_by = ?
            WHERE id = ?
              AND tenant_id = ?::uuid
              AND legal_entity_id = ?::uuid
              AND version = ?
              AND posted_at IS NULL
        """,
            req.bankName(),
            req.iban(),
            req.accountNumber(),
            UUID.fromString(ctx.userId()),
            id,
            UUID.fromString(ctx.tenantId()),
            UUID.fromString(ctx.legalEntityId()),
            version
        );
    }
}
```

---

## 5) Helper Classes (in Starter or Utils)

### Hashing.java
```java
package com.waangu.platform.util;

import org.apache.commons.codec.digest.DigestUtils;

public final class Hashing {
    public static String sha256(String input) {
        return DigestUtils.sha256Hex(input);
    }
    private Hashing() {}
}
```

---

## 6) Configuration

### application.yml
```yaml
spring:
  application:
    name: erp-ms-tresorerie-backend
  
  datasource:
    # Managed by RoutingDataSource - config via env vars
    url: ${DB_POOLED_URL:jdbc:postgresql://localhost:5432/erp_pooled}
    username: ${DB_POOLED_USER:postgres}
    password: ${DB_POOLED_PASSWORD:postgres}

  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${KEYCLOAK_ISSUER_URI:https://keycloak.waangu.com/auth/realms/waangu}
          jwk-set-uri: ${KEYCLOAK_JWK_URI:https://keycloak.waangu.com/auth/realms/waangu/protocol/openid-connect/certs}

waangu:
  moduleId: ERP_TREASURY_CORE
  tenantRegistryUrl: ${TENANT_REGISTRY_URL:http://tenant-registry:8080}
  translationUrl: ${TRANSLATION_URL:http://translation-service:8080}
  
flyway:
  enabled: true
  locations: classpath:db/migration
  baseline-on-migrate: true

logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [tenant:%X{tenant_id}] [corr:%X{correlation_id}] - %msg%n"
```

---

## 7) Tests

### CrossTenantRlsTest.java
```java
package com.waangu.erp.treasury.crosstenant;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers
class CrossTenantRlsTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("erp_test")
            .withUsername("postgres")
            .withPassword("postgres");

    JdbcTemplate jdbc;

    @BeforeEach
    void setup() {
        DataSource ds = DataSourceBuilder.create()
                .url(postgres.getJdbcUrl())
                .username(postgres.getUsername())
                .password(postgres.getPassword())
                .build();

        jdbc = new JdbcTemplate(ds);

        // Apply migrations
        Flyway.configure()
                .dataSource(ds)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void tenantB_mustNotSee_tenantA_data() {
        UUID tenantA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID tenantB = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        UUID legalEntity = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        // Tenant A: insert bank account
        jdbc.execute("BEGIN");
        jdbc.execute("SELECT set_config('app.current_tenant', '" + tenantA + "', true)");
        jdbc.execute("SELECT set_config('app.current_legal_entity', '" + legalEntity + "', true)");

        jdbc.update("""
            INSERT INTO treasury_bank_account(
                id, tenant_id, legal_entity_id, country_code,
                currency_code, bank_name, name_i18n_key, created_by
            ) VALUES (?,?,?,?,?,?,?,?)
        """,
            UUID.randomUUID(),
            tenantA,
            legalEntity,
            "BI",
            "BIF",
            "Bank A",
            "key.a",
            UUID.randomUUID()
        );
        jdbc.execute("COMMIT");

        // Tenant B: read (should see NOTHING)
        jdbc.execute("BEGIN");
        jdbc.execute("SELECT set_config('app.current_tenant', '" + tenantB + "', true)");
        jdbc.execute("SELECT set_config('app.current_legal_entity', '" + legalEntity + "', true)");

        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM treasury_bank_account",
            Integer.class
        );

        jdbc.execute("COMMIT");

        // MUST be 0 (RLS isolation)
        assertEquals(0, count, "Tenant B must not see Tenant A data");
    }

    @Test
    void withoutSettingTenant_queryShouldReturnZero() {
        UUID tenantA = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID legalEntity = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

        // Insert with tenant set
        jdbc.execute("BEGIN");
        jdbc.execute("SELECT set_config('app.current_tenant', '" + tenantA + "', true)");
        jdbc.execute("SELECT set_config('app.current_legal_entity', '" + legalEntity + "', true)");

        jdbc.update("""
            INSERT INTO treasury_bank_account(
                id, tenant_id, legal_entity_id, country_code,
                currency_code, bank_name, name_i18n_key, created_by
            ) VALUES (?,?,?,?,?,?,?,?)
        """,
            UUID.randomUUID(),
            tenantA,
            legalEntity,
            "BI",
            "BIF",
            "Bank A",
            "key.a",
            UUID.randomUUID()
        );
        jdbc.execute("COMMIT");

        // Query WITHOUT setting tenant
        jdbc.execute("BEGIN");
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM treasury_bank_account",
            Integer.class
        );
        jdbc.execute("COMMIT");

        // Should be 0 (RLS blocks access)
        assertEquals(0, count, "Without tenant setting, RLS must block access");
    }
}
```

---

## 8) pom.xml (dependencies)

```xml
<dependencies>
    <!-- Waangu Platform Starter (George) -->
    <dependency>
        <groupId>com.waangu.platform</groupId>
        <artifactId>waangu-saas-spring-starter</artifactId>
        <version>1.0.0</version>
    </dependency>

    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-jdbc</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>

    <!-- PostgreSQL -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>

    <!-- Flyway -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <!-- Commons Codec (hashing) -->
    <dependency>
        <groupId>commons-codec</groupId>
        <artifactId>commons-codec</artifactId>
    </dependency>

    <!-- Caffeine (caching) -->
    <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
    </dependency>

    <!-- Test -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>postgresql</artifactId>
        <scope>test</scope>
    </dependency>

    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```
