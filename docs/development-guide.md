# Development Guide

_Setup, build, test, and run instructions for the Data Quality Service._

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 17 (source), 21 (test JVM) | Java 21 required for Spark test execution; Java 25+ breaks Hadoop UGI |
| Maven | 3.8+ | For dqs-spark builds |
| Python | 3.12+ | For dqs-serve and dqs-orchestrator |
| uv | Latest | Python package manager (replaces pip) |
| Node.js | 22+ | For dqs-dashboard |
| npm | 10+ | Bundled with Node.js |
| Docker | 20+ | For PostgreSQL and local services |
| Docker Compose | v2+ | Multi-service orchestration |
| PostgreSQL | 15 | Via Docker Compose or standalone |

---

## Quick Start

### 1. Start Infrastructure

```bash
# From project root — starts Postgres, serve, and dashboard
docker-compose up -d
```

This starts:
- **PostgreSQL 15** on port 5432 (user: `postgres`, password: `localdev`, db: `postgres`)
- **dqs-serve** on port 8000 (FastAPI)
- **dqs-dashboard** on port 5173 (Vite dev server)

### 2. Initialize Database Schema and Test Data

The database needs schema tables, views, and test fixture data before the API or dashboard will return meaningful results.

```bash
# Connect to the running Postgres container
docker exec -i $(docker-compose ps -q postgres) psql -U postgres postgres

# Or from outside Docker (if Postgres is accessible on localhost:5432):
psql -U postgres -h localhost -p 5432 postgres
```

Execute the schema files **in this exact order**:

```sql
-- Step 1: Create all tables with temporal pattern
\i dqs-serve/src/serve/schema/ddl.sql

-- Step 2: Create active-record views (depend on tables)
\i dqs-serve/src/serve/schema/views.sql

-- Step 3: Seed test fixture data (realistic baseline for development)
\i dqs-serve/src/serve/schema/fixtures.sql
```

Alternatively, as a single command:

```bash
cat dqs-serve/src/serve/schema/ddl.sql \
    dqs-serve/src/serve/schema/views.sql \
    dqs-serve/src/serve/schema/fixtures.sql \
  | docker exec -i $(docker-compose ps -q postgres) psql -U postgres postgres
```

### 3. Verify the Setup

```bash
# Check API is responding with fixture data
curl http://localhost:8000/api/summary

# Open dashboard in browser
open http://localhost:5173
```

---

## Mock Data and DB History Build-Out

The fixtures in `dqs-serve/src/serve/schema/fixtures.sql` provide a realistic baseline for local development. Here is what they populate:

### What the Fixtures Include

| Category | Data | Purpose |
|----------|------|---------|
| **Orchestration runs** | 2 batch runs (retail/alpha, commercial/beta) both with errors | Tests run-level status display |
| **7-day history** | 7 daily runs for `sales_daily` dataset (all PASS, score 98.50) | Enables trend charts, volume baseline |
| **Mixed statuses** | 5 datasets: 1 PASS, 1 WARN, 2 FAIL, 1 legacy | Tests all status paths in dashboard |
| **Volume metrics** | 7 daily row counts (96k-107k range) | Realistic volume anomaly detection baseline |
| **Freshness metrics** | 2h (healthy) and 72h (stale) | Tests freshness threshold display |
| **Schema metrics** | 3 missing columns (FAIL) | Tests schema drift display |
| **OPS metrics** | 87% null rate (WARN) | Tests operational check display |
| **Detail metrics** | 6 JSONB types (string, number, boolean, array, object, nested) | Full JSONB support validation |
| **LOB lookups** | LOB_RETAIL, LOB_COMMERCIAL, LOB_LEGACY | Reference data for LOB resolution |
| **Check configs** | 4 patterns (wildcard, specific disable, explosion_level) | Tests config-driven check enablement |
| **Dataset enrichment** | 3 patterns with custom_weights and SLA hours | Tests enrichment lookup and custom weighting |

### Adding More Test Data

To add additional datasets or history for development, follow the temporal data pattern:

```sql
-- Always set expiry_date to the sentinel for active records
INSERT INTO dq_run
    (dataset_name, partition_date, lookup_code, check_status, dqs_score, rerun_number, expiry_date)
VALUES
    ('lob=wealth/src_sys_nm=gamma/dataset=portfolios', '2026-04-03', 'LOB_WEALTH', 'PASS', 92.00, 0, '9999-12-31 23:59:59');

-- Add the LOB lookup if it's a new LOB
INSERT INTO lob_lookup (lookup_code, lob_name, owner, classification, expiry_date)
VALUES ('LOB_WEALTH', 'Wealth Management', 'Bob Jones', 'Tier 1 Critical', '9999-12-31 23:59:59');
```

### Resetting Test Data

```bash
# Drop all tables and recreate from scratch
cat dqs-serve/src/serve/schema/ddl.sql \
    dqs-serve/src/serve/schema/views.sql \
    dqs-serve/src/serve/schema/fixtures.sql \
  | docker exec -i $(docker-compose ps -q postgres) psql -U postgres postgres
```

---

## Component-Specific Development

### dqs-spark

```bash
cd dqs-spark

# Build (compile + package)
mvn clean package -DskipTests

# Run tests (requires Java 21 JVM — configured in pom.xml Surefire plugin)
mvn test

# Run a single test class
mvn test -Dtest=VolumeCheckTest

# Run the job locally (against local Postgres + local file paths)
spark-submit target/dqs-spark-1.0.0.jar \
  --parent-path /path/to/hdfs/data \
  --date 20260402
```

**Testing notes:**
- Tests use H2 in-memory database for JDBC tests (SQL must be compatible with both H2 and Postgres)
- H2 numeric columns with fractional values require `DECIMAL(20,5)` in test DDL
- Spark tests use `master("local[1]")` with a static SparkSession per test class
- Test JVM is Java 21 (configured in `pom.xml` Surefire `<jvm>` path)

### dqs-serve

```bash
cd dqs-serve

# Install dependencies
uv sync

# Run the server locally
uv run uvicorn --app-dir src serve.main:app --reload --port 8000

# Run unit tests (default — excludes integration tests)
uv run pytest

# Run integration tests (requires running Postgres with schema)
uv run pytest -m integration

# Run all tests
uv run pytest -m ''

# Run a specific test file
uv run pytest tests/test_routes/test_summary.py -v
```

**Testing notes:**
- Unit tests use a mock DB session (auto-injected via `conftest.py` `override_db_dependency` fixture)
- Integration tests require a real PostgreSQL instance with schema (use `docker-compose up postgres`)
- Integration tests are excluded by default (`pyproject.toml: addopts = -m 'not integration'`)
- The `seeded_client` fixture runs `ddl.sql` → `views.sql` → `fixtures.sql` in a transaction

### dqs-orchestrator

```bash
cd dqs-orchestrator

# Install dependencies
uv sync

# Run tests
uv run pytest

# Run a specific test
uv run pytest tests/test_runner.py -v

# Run the orchestrator CLI (locally, with mocked spark-submit)
uv run python -m orchestrator.cli --config config/orchestrator.yaml --date 20260402

# Rerun specific datasets
uv run python -m orchestrator.cli --datasets "lob=retail/src_sys_nm=alpha/dataset=sales_daily" --rerun
```

**Testing notes:**
- Tests mock `subprocess.run` for spark-submit — no Spark cluster required
- Tests mock `psycopg2.connect` for DB operations
- Config files are created in tmp_path fixtures for isolation

### dqs-dashboard

```bash
cd dqs-dashboard

# Install dependencies
npm install

# Start dev server (with API proxy to localhost:8000)
npm run dev

# Run tests
npm test

# Run tests in watch mode
npm run test:watch

# Lint
npm run lint

# Build for production
npm run build
```

**Testing notes:**
- Vitest + React Testing Library + jest-dom
- Tests render with ThemeProvider + QueryClientProvider + MemoryRouter wrappers
- Fresh QueryClient per test (staleTime: 0)
- `vi.mock` for API calls and component isolation
- Tests in `tests/` directory (mirroring `src/` structure)

---

## Workspace-Level Commands

```bash
# From project root

# Run acceptance tests (requires docker-compose services running)
uv run pytest tests/acceptance/ -m integration

# Run unit acceptance tests only
uv run pytest tests/acceptance/

# Lint Python code (ruff)
uv run ruff check .

# Format Python code
uv run ruff format .
```

---

## Environment Variables

| Variable | Component | Default | Description |
|----------|-----------|---------|-------------|
| `DATABASE_URL` | dqs-serve | `postgresql://postgres:localdev@localhost:5432/postgres` | PostgreSQL connection string |

All other configuration is file-based per component:

| Component | Config File | Key Settings |
|-----------|-------------|--------------|
| dqs-spark | `config/application.properties` | JDBC URL, Spark master |
| dqs-serve | `config/serve.yaml` | DB pool size, server port |
| dqs-orchestrator | `config/orchestrator.yaml` | Spark submit settings, email, DB |
| dqs-orchestrator | `config/parent_paths.yaml` | HDFS parent paths |

---

## Deployment Notes

- **docker-compose** runs: Postgres (5432), serve (8000), dashboard (5173)
- **dqs-spark** runs outside Docker on Spark cluster via `spark-submit`
- **dqs-orchestrator** runs outside Docker on scheduling server (cron or similar)
- Both external components connect to the same PostgreSQL instance
- Schema DDL is owned by dqs-serve — all schema changes go through `dqs-serve/src/serve/schema/`
