# GitHub Actions CI — Microservice #1 (erp-ms-tresorerie-backend)

---

## .github/workflows/ci.yml

```yaml
name: ci

on:
  pull_request:
    branches: ["main", "master"]
  push:
    branches: ["main", "master"]
  workflow_dispatch: {}

concurrency:
  group: ci-${{ github.ref }}
  cancel-in-progress: true

env:
  JAVA_VERSION: "21"
  MAVEN_OPTS: "-Dstyle.color=always"
  MVN: "./mvnw -B -ntp"

jobs:
  unit:
    name: unit
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven

      - name: Forbid local re-implementation of platform classes
        run: |
          forbidden=("TenantContextFilter" "TenantRegistryClient" "RoutingDataSource" \
                     "DbSessionInitializer" "SaaSContractGuard" "ForbiddenBodyFieldsFilter" \
                     "IdempotencyService" "AuditLogService" "OutboxService" "I18nClient" \
                     "CopilotIntentController")
          for c in "${forbidden[@]}"; do
            if grep -R --line-number "class $c" src/main/java 2>/dev/null | grep -v "// import"; then
              echo "❌ FORBIDDEN: $c must come from waangu-saas-spring-starter"
              exit 1
            fi
          done
          echo "✅ No platform class duplication detected"

      - name: Run unit tests
        run: |
          $MVN -Dtest='*UnitTest,*Test' test

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: unit-reports
          path: |
            target/surefire-reports/**
            target/failsafe-reports/**

  integration:
    name: integration
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven

      - name: Run integration tests
        run: |
          $MVN -Dtest='*IT' test

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: integration-reports
          path: |
            target/surefire-reports/**
            target/failsafe-reports/**

  cross_tenant:
    name: cross_tenant
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven

      - name: Run cross-tenant RLS tests
        run: |
          $MVN -Dtest='*CrossTenant*Test' test

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: cross-tenant-reports
          path: |
            target/surefire-reports/**
            target/failsafe-reports/**

  migrations:
    name: migrations
    runs-on: ubuntu-latest
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_PASSWORD: postgres
          POSTGRES_USER: postgres
          POSTGRES_DB: erp_test
        ports:
          - "5432:5432"
        options: >-
          --health-cmd="pg_isready -U postgres"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=10

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven

      - name: Install Node (for AJV manifest validation)
        uses: actions/setup-node@v4
        with:
          node-version: "20"

      - name: AJV validate manifest.json
        run: |
          npm i --silent ajv
          node -e "const Ajv=require('ajv');const fs=require('fs');const ajv=new Ajv({allErrors:true}); \
          const schema=JSON.parse(fs.readFileSync('manifest.schema.json')); \
          const data=JSON.parse(fs.readFileSync('manifest.json')); \
          const validate=ajv.compile(schema); \
          if(!validate(data)){console.error('❌ MANIFEST_INVALID');console.error(validate.errors);process.exit(1);} \
          console.log('✅ manifest valid');"

      - name: Flyway validate
        env:
          FLYWAY_URL: jdbc:postgresql://localhost:5432/erp_test
          FLYWAY_USER: postgres
          FLYWAY_PASSWORD: postgres
        run: |
          $MVN -DskipTests=true \
            -Dflyway.url="$FLYWAY_URL" \
            -Dflyway.user="$FLYWAY_USER" \
            -Dflyway.password="$FLYWAY_PASSWORD" \
            flyway:validate

      - name: Flyway migrate (apply on ephemeral DB)
        env:
          FLYWAY_URL: jdbc:postgresql://localhost:5432/erp_test
          FLYWAY_USER: postgres
          FLYWAY_PASSWORD: postgres
        run: |
          $MVN -DskipTests=true \
            -Dflyway.url="$FLYWAY_URL" \
            -Dflyway.user="$FLYWAY_USER" \
            -Dflyway.password="$FLYWAY_PASSWORD" \
            flyway:migrate

      - name: RLS policy verification
        env:
          DB_URL: postgresql://postgres:postgres@localhost:5432/erp_test
        run: |
          sudo apt-get update -y
          sudo apt-get install -y postgresql-client
          
          echo "Checking RLS policies..."
          missing=$(psql "$DB_URL" -t -A <<'SQL'
          WITH t AS (
              SELECT c.relname AS table_name
              FROM pg_class c
              JOIN pg_namespace n ON n.oid = c.relnamespace
              WHERE n.nspname = 'public'
                AND c.relkind = 'r'
                AND (
                    c.relname LIKE 'treasury_%'
                    OR c.relname IN ('coa_accounts', 'accounting_settings', 'audit_log', 'outbox_event', 'idempotency_key')
                )
          ),
          p AS (
              SELECT polrelid::regclass::text AS table_name
              FROM pg_policy
          )
          SELECT t.table_name
          FROM t
          LEFT JOIN p ON p.table_name = t.table_name
          WHERE p.table_name IS NULL;
          SQL
          )
          
          if [ -n "$missing" ]; then
              echo "❌ MISSING RLS POLICIES ON:"
              echo "$missing"
              exit 1
          fi
          
          echo "✅ All tenant-scoped tables have RLS policies"

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: migrations-artefacts
          path: |
            manifest.json
            manifest.schema.json
            db/migration/**
            target/**

  secrets_scan:
    name: secrets_scan
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0

      - name: Run gitleaks
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

  perf_test:
    name: perf_test
    runs-on: ubuntu-latest
    # Optional: not blocking by default
    continue-on-error: true
    services:
      postgres:
        image: postgres:16
        env:
          POSTGRES_PASSWORD: postgres
          POSTGRES_USER: postgres
          POSTGRES_DB: erp_test
        ports: ["5432:5432"]
        options: >-
          --health-cmd="pg_isready -U postgres"
          --health-interval=10s
          --health-timeout=5s
          --health-retries=10

    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: ${{ env.JAVA_VERSION }}
          cache: maven

      - name: Flyway migrate (for perf tests)
        env:
          FLYWAY_URL: jdbc:postgresql://localhost:5432/erp_test
          FLYWAY_USER: postgres
          FLYWAY_PASSWORD: postgres
        run: |
          $MVN -DskipTests=true \
            -Dflyway.url="$FLYWAY_URL" \
            -Dflyway.user="$FLYWAY_USER" \
            -Dflyway.password="$FLYWAY_PASSWORD" \
            flyway:migrate

      - name: Run performance tests
        run: |
          $MVN -Dtest='*PerfTest' test

      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: perf-test-reports
          path: |
            target/surefire-reports/**
            target/failsafe-reports/**
```

---

## .github/workflows/waangu_pr_guard_strict.yml

```yaml
name: waangu_pr_guard_strict

on:
  pull_request:
    types: [opened, edited, synchronize, reopened, ready_for_review]

permissions:
  contents: read
  pull-requests: read
  checks: read

jobs:
  guard:
    name: waangu_pr_guard_strict
    runs-on: ubuntu-latest
    steps:
      - name: Enforce PR template + checklist 20/20 + audit-ready rules + required CI jobs
        uses: actions/github-script@v7
        with:
          script: |
            const pr = context.payload.pull_request;
            const body = pr.body || "";
            const owner = context.repo.owner;
            const repo = context.repo.repo;

            const fail = (msg) => core.setFailed("❌ WAANGU GUARD: " + msg);

            // ---- A) Require non-empty proof links
            const requiredProofKeys = [
              "CI_PIPELINE_LINK",
              "CI_UNIT_JOB_LINK",
              "CI_INTEGRATION_JOB_LINK",
              "CI_CROSS_TENANT_JOB_LINK",
              "CI_MIGRATIONS_JOB_LINK",
              "CI_TEST_LOGS_ARTEFACTS_LINK"
            ];

            function readField(key) {
              const re = new RegExp("^" + key + "\\s*:\\s*(.+)$", "mi");
              const m = body.match(re);
              return m ? (m[1] || "").trim() : "";
            }

            function isPlaceholder(v) {
              if (!v) return true;
              const up = v.toUpperCase();
              return v.startsWith("<") || up.includes("REQUIRED") || up.includes("FILL") || up.includes("TODO") || up === "N/A";
            }

            const proofMissing = [];
            for (const k of requiredProofKeys) {
              const v = readField(k);
              if (isPlaceholder(v)) proofMissing.push(k);
            }
            if (proofMissing.length) {
              return fail("Missing/empty proof links: " + proofMissing.join(", ") + ". Fill with real URLs.");
            }

            // ---- B) Checklist 20/20 checked
            const checked = (body.match(/^- \[x\] /gmi) || []).length;
            if (checked < 20) {
              return fail(`Checklist incomplete: ${checked}/20 checked. All 20 must be [x].`);
            }

            // ---- C) Required CI check runs exist as distinct jobs
            const requiredChecks = ["unit", "integration", "cross_tenant", "migrations"];
            const sha = pr.head.sha;

            const checksResp = await github.rest.checks.listForRef({
              owner, repo, ref: sha, per_page: 100
            });
            const checkNames = checksResp.data.check_runs.map(c => c.name);

            const missingChecks = requiredChecks.filter(n => !checkNames.includes(n));
            if (missingChecks.length) {
              return fail("Missing required CI jobs (must exist as distinct checks): " + missingChecks.join(", "));
            }

            // ---- D) Audit-ready++: If SQL changed, MIGRATION_PLAN & ROLLBACK_PLAN must be filled
            const files = await github.paginate(github.rest.pulls.listFiles, {
              owner, repo, pull_number: pr.number, per_page: 100
            });

            const sqlChanged = files.some(f =>
              f.filename.endsWith(".sql") ||
              f.filename.includes("db/migration") ||
              f.filename.includes("migrations")
            );

            if (sqlChanged) {
              const mig = readField("MIGRATION_PLAN");
              const rbk = readField("ROLLBACK_PLAN");
              if (isPlaceholder(mig)) return fail("SQL changed but MIGRATION_PLAN is missing/placeholder.");
              if (isPlaceholder(rbk)) return fail("SQL changed but ROLLBACK_PLAN is missing/placeholder.");
            }

            core.info("✅ WAANGU GUARD PASS: proofs ok, checklist ok, required jobs ok, audit-ready rules ok.");
```

---

## Branch Protection Settings (GitHub Settings → Branches)

**Rule for**: `main` / `production`

### Required settings:
- ✅ **Require a pull request before merging**
- ✅ **Require approvals**: 2
- ✅ **Dismiss stale pull request approvals when new commits are pushed**
- ✅ **Require status checks to pass before merging**
- ✅ **Require branches to be up to date before merging**
- ✅ **Require conversation resolution before merging**
- ✅ **Include administrators**

### Required status checks:
- `waangu_pr_guard_strict` ✅ (CRITICAL)
- `unit` ✅
- `integration` ✅
- `cross_tenant` ✅
- `migrations` ✅
- `secrets_scan` ✅

---

## CODEOWNERS

```
# Platform / Tenant / Security
/tenant/            @george-platform
/security/          @george-platform @tresor-cto

# i18n / glossary
/i18n/              @george-platform @landry-frontend

# Copilot
/copilot/           @cesar-copilot

# DB migrations (FinTech-grade - requires QA + DevOps approval)
/db/migration/      @didier-qa @hugues-devops

# Manifest & contracts
/manifest.json      @george-platform @jackson-sdk
/*.md               @george-platform
```

---

## Artifacts Published

After each CI run, the following artifacts are uploaded:

1. **unit-reports**: Surefire reports from unit tests
2. **integration-reports**: Surefire reports from integration tests
3. **cross-tenant-reports**: RLS isolation test results
4. **migrations-artefacts**: 
   - manifest.json
   - manifest.schema.json
   - SQL migration files
   - Flyway output
   - RLS verification results

---

## Guard Enforcement

The `waangu_pr_guard_strict` workflow will **FAIL** the PR if:

1. ❌ Any of the 6 proof link fields are empty/placeholder
2. ❌ Checklist is not 20/20 checked (`- [x]`)
3. ❌ Any of the 4 required CI jobs (unit/integration/cross_tenant/migrations) is missing
4. ❌ SQL files changed but MIGRATION_PLAN or ROLLBACK_PLAN is empty/placeholder

**Result**: PR cannot be merged (status check required + failing)
