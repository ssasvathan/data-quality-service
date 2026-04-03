---
stepsCompleted: [1, 2, 3, 4, 5, 6, 7, 8]
lastStep: 8
status: 'complete'
completedAt: '2026-04-03'
inputDocuments:
  - _bmad-output/planning-artifacts/prd.md
  - _bmad-output/planning-artifacts/ux-design-specification.md
  - _bmad-output/planning-artifacts/implementation-readiness-report-2026-04-02.md
workflowType: 'architecture'
project_name: 'data-quality-service'
user_name: 'Sas'
date: '2026-04-03'
---

# Architecture Decision Document

_This document builds collaboratively through step-by-step discovery. Sections are appended as we work through each architectural decision together._

## Project Context Analysis

### Requirements Overview

**Functional Requirements:**

58 FRs spanning 8 categories define a multi-component data quality platform:

| Category | FRs | Architectural Impact |
|----------|-----|---------------------|
| Data Discovery & Ingestion (FR1-7) | 7 | Extensible HDFS path scanner, multi-format reader (Avro/Parquet), backfill support |
| Quality Check Execution (FR8-22) | 15 | 4-tier check architecture via strategy pattern interface, batch DB writes, eventAttribute explosion, configurable weights |
| Orchestration & Run Management (FR23-30) | 8 | Python CLI, one spark-submit per parent path, two-layer failure isolation, dataset-level rerun |
| Data Model & Audit (FR31-35) | 5 | Soft-delete temporal pattern with TIMESTAMP columns, sentinel-based expiry, DB-enforced uniqueness on natural keys |
| Reporting Dashboard (FR36-45) | 10 | 3-level drill-down (Summary Card Grid -> LOB Stats+Table -> Dataset Master-Detail Split), search, trend visualization |
| Reference Data Resolution (FR46-48) | 3 | Loosely coupled scaffold, twice-daily cache, LOB resolution via lookup table join in SQL |
| API & Integration (FR49-52) | 4 | Data-model-driven REST API, MCP tools wrapping same endpoints, summary email composed by orchestrator |
| Test Data (FR53-57) | 5 | Multi-format fixtures, mixed anomalies, legacy path patterns, mocked DB history |
| Executive Reporting - Phase 3 (FR58) | 1 | Strategic dashboards deferred to Phase 3 |

**Non-Functional Requirements:**

25 NFRs drive key architectural constraints:

- **Performance:** Dashboard < 2s load, API < 500ms standard / < 2s trends, Spark run < 3 hours, batch DB writes (single write per dataset). Postgres comfortable at expected scale (~1-2M rows under 90-day retention).
- **Security:** No app-level auth (network boundary). Data sensitivity enforced via interface contract — DqCheck returns aggregate metrics only, no raw PII/PCI persisted.
- **Scalability:** 500 datasets, 15 check types, 90 days = ~1-2M rows. Generic metric consumption for new check types. Materialized views deferred — not needed at this scale.
- **Integration:** HDFS (Spark on YARN), Postgres (JDBC + SQLAlchemy), SMTP (summary email from orchestrator), Dataset registration service (future scaffold), MCP (FastMCP).
- **Reliability:** Two-layer failure isolation (per-dataset in Spark, per-parent-path in orchestrator), no data loss (atomic batch writes), no HA requirement.

**Scale & Complexity:**

- Primary domain: Full-stack data platform (batch processing + REST API + SPA + MCP)
- Complexity level: High
- Estimated architectural components: 5 (Spark job, Python orchestrator, FastAPI serve, React SPA, MCP layer)

### Technical Constraints & Dependencies

- **Brownfield restart:** Existing prototype (~15-20%) provides validated domain knowledge and reference implementations. All components designed fresh — DB schema (new temporal pattern), Spark checks (new extensible interface), serve layer (new FastAPI design), frontend (React replaces vanilla JS). ~5-10% reusable code, ~80% reusable domain knowledge.
- **Multi-runtime coordination:** JVM (Spark) and Python (orchestrator + serve) share Postgres as the integration layer. No direct runtime communication. Shared sentinel constant (`9999-12-31 23:59:59`) defined once per runtime.
- **Temporal pattern:** All tables use `create_date` + `expiry_date` as `TIMESTAMP` type. Active records: `expiry_date = '9999-12-31 23:59:59'`. Composite unique constraints on natural business keys + `expiry_date` enforce no duplicate active records at DB level. Active-record views (`v_*_active`) filter on sentinel for query safety.
- **Postgres as integration layer:** All DQS state in Postgres. JDBC from Spark, SQLAlchemy from serve layer. Components don't call each other directly. Composite indexes on natural key + expiry_date for primary query patterns. LOB resolution via lookup table join in SQL.
- **Orchestrator architecture:** Python CLI managing 5-10 spark-submit invocations (one per parent path). Spark job discovers and processes all datasets within a parent path. Dataset-level rerun via `--datasets` filter parameter. Summary email composed by orchestrator post-run with actionable rerun commands.
- **Check extensibility:** Strategy pattern interface (`DqCheck`) in Spark. All checks write to generic metric tables (`dq_metric_numeric`, `dq_metric_detail`). Serve layer, API, and dashboard have zero check-type awareness. Adding a new check = new Java class + factory registration + check_config row.
- **Data sensitivity boundary:** Enforced via `DqMetric` interface contract — checks output aggregate metrics only. Detail metrics may contain field names and schema structures but never source data values. Architectural rule documented and enforced via code review.
- **Configuration landscape:** Parent paths in YAML config file. Check enablement and dataset enrichment in DB tables. Runtime parameters (date, rerun flags, dataset filter) via CLI arguments. No admin UI for MVP.
- **Docker-compose deployment:** Single-instance. No orchestration platform, no HA, no health probes. Each component independently deployable.
- **Timezone:** All DQS operational timestamps are Eastern time natively. No conversion layer. Freshness check reads UTC `source_event_timestamp` from HDFS, computes staleness metric, stores only the metric.
- **Unidirectional dependency chain:** Postgres schema -> Spark + Orchestrator -> Serve layer + API -> Dashboard + MCP (parallel consumers). No circular dependencies.

### Cross-Cutting Concerns Identified

- **Audit trail / temporal pattern:** Every table uses `TIMESTAMP` columns with sentinel-based expiry. DB-enforced uniqueness prevents duplicate active records across all runtimes. Active-record views prevent stale data leaks in queries.
- **Error handling / failure isolation:** Two layers — per-dataset inside Spark (failed datasets get error messages, others continue), per-parent-path in orchestrator (failed paths don't halt others). Batch writes are atomic per parent path (single transaction). Summary email categorizes parent path failures vs dataset failures with rerun commands.
- **Check extensibility:** Strategy pattern interface in Spark. Generic metric tables. Zero check-type awareness in serve/API/dashboard. DQS Score computed in Spark as a weighted composite — the one component that knows check types.
- **Data sensitivity boundary:** Interface contract constrains check output to aggregates. No raw PII/PCI in Postgres, API, dashboard, or MCP.
- **Cross-runtime consistency:** Sentinel timestamp constant and temporal pattern logic must be identical in Java and Python. Defined once per runtime, validated by shared test cases.
- **Reference data resolution:** Loosely coupled to external registration service. LOB/owner resolved via lookup table join in SQL. Cached with twice-daily refresh. "N/A" fallback for unresolved codes.

## Starter Template Evaluation

### Primary Technology Domain

Full-stack data platform with 5 distinct components. Each component is fully self-contained with its own config, dependencies, and build. No shared resources — designed to split into independent repos without changes.

### Current Versions (Verified April 2026)

| Technology | Version | Component |
|---|---|---|
| Vite | 8.0.3 | dqs-dashboard |
| React | 19.x | dqs-dashboard |
| MUI (Material UI) | 7.3.9 | dqs-dashboard |
| React Router | 7.13.2 | dqs-dashboard |
| Recharts | 3.8.1 | dqs-dashboard |
| TypeScript | 5.x | dqs-dashboard |
| FastAPI | 0.135.1 | dqs-serve |
| SQLAlchemy | 2.0.48 | dqs-serve |
| FastMCP | 3.2.0 | dqs-serve (MCP layer) |
| Python | 3.12+ | dqs-serve, dqs-orchestrator |
| Apache Spark | Cluster-dependent | dqs-spark |
| Java | Cluster-dependent (17 or 21) | dqs-spark |

### Component Initialization

#### dqs-dashboard (React SPA)

```bash
npm create vite@latest dqs-dashboard -- --template react-ts
cd dqs-dashboard
npm install @mui/material @emotion/react @emotion/styled
npm install recharts react-router
```

Vite 8 with Rolldown bundler. MUI 7 theming per UX spec. React Router 7 for deep-linkable routes. Recharts 3 for sparklines/trends.

#### dqs-serve (FastAPI + MCP)

```bash
uv init dqs-serve
cd dqs-serve
uv add fastapi sqlalchemy psycopg2-binary uvicorn fastmcp
```

FastAPI 0.135 with Pydantic 2 validation. SQLAlchemy 2.0 models with temporal pattern and active-record views. FastMCP 3.2 tools wrapping the same API endpoints. Owns the Postgres schema DDL.

#### dqs-orchestrator (Python CLI)

```bash
uv init dqs-orchestrator
cd dqs-orchestrator
uv add psycopg2-binary pyyaml
```

Minimal Python CLI. Reads parent paths from its own YAML config. Manages spark-submit subprocesses. Queries Postgres for summary email aggregation. Sends SMTP. No web framework.

#### dqs-spark (Java/Maven)

```bash
mvn archetype:generate -DgroupId=com.bank.dqs -DartifactId=dqs-spark
```

Maven project with Spark as `provided` dependency (version matches cluster). DqCheck strategy interface, check implementations, HDFS path scanner, batch JDBC writer.

### Self-Containment Principles

- Each component has its own `config/` directory — no shared config
- DB connection details duplicated per component (Spark JDBC config, SQLAlchemy config, orchestrator psycopg2 config)
- Postgres schema DDL owned by `dqs-serve`
- Sentinel timestamp constant (`9999-12-31 23:59:59`) defined independently in each runtime
- `docker-compose.yml` is deployment infrastructure, not a shared resource
- Integration contract is the Postgres schema — components communicate only through the database

## Core Architectural Decisions

### Decision Priority Analysis

**Critical Decisions (Block Implementation):**
All critical decisions resolved in Steps 2-3 and Party Mode — temporal pattern, component architecture, check extensibility, orchestrator design, data sensitivity boundary.

**Important Decisions (Shape Architecture):**
Resolved in this step — state management, error format, pagination, logging, environment config, testing.

**Deferred Decisions (Post-MVP):**
- API pagination (revisit if LOB exceeds hundreds of datasets)
- Materialized views (revisit if query performance degrades)
- Admin UI for config tables (SQL access sufficient for MVP)
- Dark mode (UX spec explicitly deferred)
- E2E testing (component tests sufficient for MVP)

### Data Architecture

| Decision | Choice | Rationale |
|---|---|---|
| Database | Postgres | Established from PRD. JDBC from Spark, SQLAlchemy from Python. |
| Temporal pattern | `TIMESTAMP` columns, sentinel `9999-12-31 23:59:59`, never null | DB-enforced uniqueness on natural keys + expiry_date. Active-record views for query safety. |
| LOB resolution | Lookup table join in SQL | Serve layer maintains LOB mapping table, refreshed twice daily. Summary queries use SQL GROUP BY. |
| Indexing | Composite indexes on natural key + expiry_date | Covers primary query patterns. No materialized views at expected scale (~1-2M rows). |
| Pagination | No pagination for MVP | Full dataset lists per LOB (50-100 records). Client-side sorting in DataGrid. Revisit if needed. |

### Authentication & Security

| Decision | Choice | Rationale |
|---|---|---|
| App authentication | None | Internal network/VPN boundary security. No user auth, no RBAC. |
| HDFS authentication | Kerberos (production), username/password (dev) | Bank infrastructure requirement. |
| Data sensitivity | DqMetric interface contract | Checks output aggregates only. Detail metrics contain field names, never values. |

### API & Communication Patterns

| Decision | Choice | Rationale |
|---|---|---|
| API style | Data-model-driven REST (FastAPI) | Generic drill-down endpoints, not check-specific. |
| MCP layer | FastMCP 3.2 wrapping same API endpoints | Same data, different interface. |
| Error format | FastAPI default shape: `{"detail": "message", "error_code": "NOT_FOUND"}` | Consistent, simple. Internal tool with one frontend consumer. |
| Inter-component communication | Postgres only | No direct calls between components. DB is the integration layer. |
| Summary email | Orchestrator queries Postgres, formats and sends via SMTP | No serve layer dependency for batch operations. |

### Frontend Architecture

| Decision | Choice | Rationale |
|---|---|---|
| Framework | React 19 + Vite 8 + TypeScript | Modern toolchain, Rolldown bundler. |
| Design system | MUI 7 with custom theme | Muted palette, semantic colors per UX spec. |
| Routing | React Router 7 | Deep-linkable routes: `/summary`, `/lob/:id`, `/dataset/:id`. |
| Charts | Recharts 3 | Sparklines and trend lines. Lightweight, React-native. |
| State/data fetching | React Query (TanStack Query) | Caching between drill-down levels, skeleton loading, stale-while-revalidate. Global time range change invalidates all queries. |
| Layout | 3-level drill-down per UX spec | Card Grid -> Stats+Table -> Master-Detail Split. |

### Infrastructure & Deployment

| Decision | Choice | Rationale |
|---|---|---|
| Deployment | Docker-compose, single instance | No HA, no K8s. Downtime during maintenance acceptable. |
| Environment config | Environment variables for deployment values, config files for app settings | Standard docker-compose pattern. `.env` file sets all vars. |
| Logging | SLF4J/Log4j for dqs-spark. Structured console logging for dqs-orchestrator and dqs-serve. `run_id` correlation across all components. | Standard per-runtime. Console logging for Python components. |
| Testing | JUnit (Spark), pytest (orchestrator + serve), Vitest (dashboard). Backend tests against real Postgres. | Temporal pattern and active-record views need real DB validation. |
| Component structure | Self-contained monorepo, designed to split | Each component owns its config, dependencies, build. Integration contract is Postgres schema. |

### Decision Impact Analysis

**Implementation Sequence:**
1. Postgres schema DDL (owned by dqs-serve) — all components depend on this
2. dqs-spark DqCheck interface + Tier 1 check implementations — produces the data
3. dqs-orchestrator CLI — invokes Spark, writes orchestration records
4. dqs-serve API endpoints + active-record views — serves the data
5. dqs-serve MCP tools — wraps API endpoints
6. dqs-dashboard — consumes API

**Cross-Component Dependencies:**
- All backend components depend on the Postgres schema (DDL in dqs-serve)
- dqs-orchestrator depends on dqs-spark JAR being built
- dqs-dashboard depends on dqs-serve API being defined (can develop against mocks)
- dqs-serve MCP depends on API endpoints existing
- No circular dependencies

## Implementation Patterns & Consistency Rules

### Naming Patterns

**JSON/API Field Naming:** snake_case throughout. Matches Postgres columns and Python conventions. Dashboard consumes as-is.

**Database Naming:**

| Element | Convention | Example |
|---|---|---|
| Tables | snake_case, singular | `dq_run`, `dq_metric_numeric`, `check_config` |
| Columns | snake_case | `dataset_name`, `partition_date`, `expiry_date` |
| Primary keys | `id` (serial/bigserial) | `dq_run.id` |
| Foreign keys | `{referenced_table}_id` | `dq_run.orchestration_run_id` |
| Indexes | `idx_{table}_{columns}` | `idx_dq_run_dataset_name_partition_date` |
| Unique constraints | `uq_{table}_{columns}` | `uq_dq_run_dataset_name_partition_date_expiry_date` |
| Views | `v_{table}_active` | `v_dq_run_active` |

**Code Naming Per Runtime:**

dqs-spark (Java): PascalCase classes, camelCase methods/variables, UPPER_SNAKE constants, lowercase dot-separated packages.

dqs-serve + dqs-orchestrator (Python): snake_case modules/files/functions/variables, PascalCase classes, UPPER_SNAKE constants.

dqs-dashboard (TypeScript/React): PascalCase components + component files + types/interfaces, camelCase hooks (`use` prefix) + utilities + variables, UPPER_SNAKE constants.

**API Endpoint Naming:**

| Convention | Rule | Example |
|---|---|---|
| Plural nouns | Resource collections | `/api/datasets`, `/api/lobs` |
| Nested drill-down | Parent/child pattern | `/api/lobs/{lob_id}/datasets` |
| snake_case params | Match Python + Postgres | `/api/datasets/{dataset_id}/metrics` |
| Query params snake_case | Consistent | `?time_range=30d&check_type=freshness` |

**API Endpoints:**

```
GET /api/summary                          → Summary card grid data
GET /api/lobs                             → All LOBs with aggregate scores
GET /api/lobs/{lob_id}/datasets           → Datasets within a LOB
GET /api/datasets/{dataset_id}            → Dataset detail
GET /api/datasets/{dataset_id}/metrics    → Check results + metrics
GET /api/datasets/{dataset_id}/trend      → Trend data for sparklines
GET /api/search?q={query}                 → Dataset search with inline scores
```

### Structure Patterns

**dqs-spark:**

```
dqs-spark/
  pom.xml
  config/
    application.properties
  src/main/java/com/bank/dqs/
    DqsJob.java
    checks/
      DqCheck.java
      FreshnessCheck.java
      VolumeCheck.java
      SchemaCheck.java
      OpsCheck.java
      DqsScoreCheck.java
      CheckFactory.java
    scanner/
      PathScanner.java
      ConsumerZoneScanner.java
    model/
      DatasetContext.java
      DqMetric.java
      MetricNumeric.java
      MetricDetail.java
    writer/
      BatchWriter.java
  src/test/java/com/bank/dqs/
    checks/
    writer/
```

**dqs-orchestrator:**

```
dqs-orchestrator/
  pyproject.toml
  config/
    parent_paths.yaml
    orchestrator.yaml
  src/
    orchestrator/
      __init__.py
      cli.py
      runner.py
      db.py
      email.py
      models.py
  tests/
    test_runner.py
    test_email.py
    test_db.py
```

**dqs-serve:**

```
dqs-serve/
  pyproject.toml
  config/
    serve.yaml
  src/
    serve/
      __init__.py
      main.py
      db/
        engine.py
        models.py
        views.py
      routes/
        summary.py
        lobs.py
        datasets.py
        search.py
      services/
        reference_data.py
        score_service.py
      mcp/
        tools.py
      schema/
        ddl.sql
        views.sql
  tests/
    conftest.py
    test_routes/
    test_services/
```

**dqs-dashboard:**

```
dqs-dashboard/
  package.json
  vite.config.ts
  tsconfig.json
  src/
    App.tsx
    main.tsx
    theme.ts
    api/
      client.ts
      queries.ts
      types.ts
    components/
      DqsScoreChip.tsx
      DatasetCard.tsx
      TrendSparkline.tsx
      DatasetInfoPanel.tsx
      SearchBar.tsx
    pages/
      SummaryPage.tsx
      LobDetailPage.tsx
      DatasetDetailPage.tsx
    layouts/
      AppLayout.tsx
  tests/
    components/
```

**Test Placement:**

| Component | Convention |
|---|---|
| dqs-spark | `src/test/java/` mirroring `src/main/java/` (Maven standard) |
| dqs-orchestrator | Top-level `tests/` directory |
| dqs-serve | Top-level `tests/` with subdirectories |
| dqs-dashboard | Top-level `tests/` by type |

### Process Patterns

**Error Handling:**
- dqs-spark: Catch per-dataset exceptions, log error, record in results, continue to next dataset. Never let one dataset crash the JVM.
- dqs-orchestrator: Catch per-spark-submit failures via exit code. Record error output. Continue to next parent path.
- dqs-serve: FastAPI exception handlers return consistent `{"detail": "...", "error_code": "..."}`. Never return stack traces.
- dqs-dashboard: React Query error states. Component-level error display ("Failed to load"), never full-page crashes for partial failures.

**Loading States (Dashboard):**
- Skeleton screens matching target layout (per UX spec)
- React Query `isLoading` / `isFetching` for skeleton vs stale-while-revalidate
- No spinning loaders — skeletons only

**Logging Levels:**
- `ERROR` — Something failed that needs attention (dataset failure, DB connection error)
- `WARN` — Something unexpected but not fatal (unresolved lookup code, slow query)
- `INFO` — Operational milestones (run started, parent path completed, email sent)
- `DEBUG` — Diagnostic detail (dataset processing start/end, query execution)

### Enforcement Guidelines

**All AI agents MUST:**
1. Follow the naming conventions for their runtime — no mixing camelCase in Python or snake_case in Java
2. Use the `EXPIRY_SENTINEL` constant, never hardcode `9999-12-31 23:59:59` inline
3. Query active-record views (`v_*_active`), never raw tables with manual expiry filters, in the serve layer
4. Return consistent error format from API endpoints
5. Place files in the documented directory structure — don't invent new top-level directories
6. Include `run_id` in log messages where available
7. Write tests in the documented location per component convention

## Project Structure & Boundaries

### Requirements to Structure Mapping

**FR1-7 (Data Discovery & Ingestion) → dqs-spark:**
FR1-3 (path scanning) → `scanner/PathScanner.java`, `scanner/ConsumerZoneScanner.java`. FR4-5 (Avro/Parquet) → `DqsJob.java`. FR6 (legacy paths) → `scanner/ConsumerZoneScanner.java` + DB lookup. FR7 (backfill) → `DqsJob.java` (`--date` param).

**FR8-22 (Check Execution) → dqs-spark:**
FR8-12 (Tier 1) → `checks/FreshnessCheck.java`, `VolumeCheck.java`, `SchemaCheck.java`, `OpsCheck.java`, `DqsScoreCheck.java`. FR13-21 (Tier 2-3) → future classes in `checks/`. FR22 (batch write) → `writer/BatchWriter.java`.

**FR23-30 (Orchestration) → dqs-orchestrator:**
FR23 (CLI trigger) → `cli.py`. FR24-25 (spark-submit + isolation) → `runner.py`. FR26-30 (run tracking, rerun, errors, counts) → `db.py`.

**FR31-35 (Data Model) → dqs-serve:**
All → `schema/ddl.sql` + `db/models.py`.

**FR36-45 (Dashboard) → dqs-dashboard:**
FR36 (summary) → `pages/SummaryPage.tsx`. FR37 (LOB drill-down) → `pages/LobDetailPage.tsx`. FR38-39 (dataset + checks) → `pages/DatasetDetailPage.tsx`. FR40 (trends) → `components/TrendSparkline.tsx`. FR41 (search) → `components/SearchBar.tsx`. FR42 (pass/fail) → `components/DqsScoreChip.tsx`. FR43-44 (executive trending/comparison) → `pages/SummaryPage.tsx` (90d time range). FR45 (source system filter) → `components/SearchBar.tsx`.

**FR46-48 (Reference Data) → dqs-serve:** `services/reference_data.py`.

**FR49-52 (API & Integration) → dqs-serve:**
FR49 (REST API) → `routes/`. FR50-51 (MCP) → `mcp/tools.py`. FR52 (summary email) → dqs-orchestrator `email.py`.

### Architectural Boundaries

**Integration Boundary — Postgres:**

```
dqs-spark ──JDBC──→ Postgres ←──SQLAlchemy── dqs-serve ←──HTTP── dqs-dashboard
                       ↑                         ↑
               psycopg2│                    FastMCP│
                       │                         │
              dqs-orchestrator              LLM/MCP consumers
```

All inter-component communication flows through Postgres. The dashboard is the only component that talks to dqs-serve via HTTP. No component calls another directly.

**Data Boundaries:**

| Boundary | Write Side | Read Side | Contract |
|---|---|---|---|
| Spark → Postgres | dqs-spark (JDBC batch write) | dqs-serve (SQLAlchemy via views) | `dq_run`, `dq_metric_numeric`, `dq_metric_detail` |
| Orchestrator → Postgres | dqs-orchestrator (psycopg2) | dqs-serve (SQLAlchemy via views) | `dq_orchestration_run` |
| Serve → Dashboard | dqs-serve (JSON responses) | dqs-dashboard (React Query) | REST API endpoints (snake_case JSON) |
| Serve → MCP | dqs-serve (FastMCP tools) | LLM consumers | Same API data, text-formatted |

**Schema Ownership:**

| Component | Owns | Consumes |
|---|---|---|
| dqs-serve | Schema DDL, active-record views, reference data cache | Reads all tables via views |
| dqs-spark | Nothing (schema consumer) | Writes to metric tables, reads check_config + dataset_enrichment |
| dqs-orchestrator | Nothing (schema consumer) | Writes to dq_orchestration_run, reads run results for email |
| dqs-dashboard | Nothing (API consumer) | Consumes REST API only, never touches DB |

### Data Flow

**Normal run:**
1. Orchestrator reads `parent_paths.yaml` → 2. For each parent path: spark-submit → 3. Spark reads check_config from Postgres → 4. Spark scans HDFS, discovers datasets → 5. Per dataset: execute enabled checks, collect results in memory → 6. Batch write all results (single transaction) → 7. Orchestrator captures exit code → 8. After all paths: orchestrator queries Postgres for summary → 9. Send summary email via SMTP → 10. Update dq_orchestration_run record.

**Dashboard consumption:**
1. User opens dashboard → SummaryPage → 2. React Query fetches `GET /api/summary` → 3. Serve queries `v_dq_run_active`, joins LOB lookup → 4. Returns aggregated LOB scores → 5. User clicks LOB → LobDetailPage → 6. Fetches `GET /api/lobs/{id}/datasets` → 7. Returns dataset list with scores, sparklines → 8. User clicks dataset → DatasetDetailPage → 9. Fetches detail + metrics + trend → 10. Full check breakdown displayed.

### Development Workflow

**Local development (docker-compose):**
- `postgres` — Postgres 16 on port 5432
- `serve` — FastAPI with hot reload on port 8000, depends on postgres
- `dashboard` — Vite dev server on port 5173, proxies API to serve

**Outside docker-compose:**
- `dqs-spark` — Developed independently. Tests use testcontainers or shared dev Postgres. Runs via spark-submit on cluster.
- `dqs-orchestrator` — Run locally pointing at dev Postgres. Mock spark-submit for unit tests.

**Production deployment (docker-compose):**
- `postgres`, `serve` (port 8000), `dashboard` (nginx serving built static files, port 80)
- dqs-spark runs on YARN cluster, dqs-orchestrator on scheduling server

## Architecture Validation Results

### Coherence Validation

**Decision Compatibility:** All technology choices are compatible. No version conflicts. Postgres as the sole integration layer eliminates inter-component coupling. Each runtime uses its native tooling (Maven/Java, uv/Python, npm/TypeScript).

**Pattern Consistency:** snake_case flows from Postgres through Python through API JSON. Each runtime follows its own language conventions internally. Active-record views, sentinel constants, and error formats are consistently specified.

**Structure Alignment:** Self-contained component structure supports the unidirectional dependency chain. FR mapping covers all requirements to specific files. Integration points are bounded at Postgres and REST API.

### Requirements Coverage

**Functional Requirements:** 57 of 58 FRs architecturally supported. FR58 (Executive Reporting) deferred to Phase 3 by design.

**Non-Functional Requirements:** All 25 NFRs addressed — performance (indexes, batch writes, caching), security (no auth, data sensitivity boundary), scalability (generic metric consumption, comfortable at ~1-2M rows), integration (HDFS, Postgres, SMTP, MCP), reliability (two-layer failure isolation, atomic writes).

### Gap Analysis Results

| # | Gap | Priority | Resolution |
|---|---|---|---|
| 1 | Test data fixtures (FR53-57) not mapped to component structure | Important | Each component stores fixtures in `tests/fixtures/`. No shared fixtures. |
| 2 | Migration from existing `spark-job/` and `serve-api/` directories | Minor | Brownfield restart. New directories created alongside. Old directories archived after rebuild. |
| 3 | Spark version | Minor | Confirmed Spark 3.x (cluster-dependent). |

### Architecture Completeness Checklist

**Requirements Analysis**
- [x] Project context thoroughly analyzed (58 FRs, 25 NFRs)
- [x] Scale and complexity assessed (500 datasets, 15 checks, ~1-2M rows)
- [x] Technical constraints identified (brownfield restart, multi-runtime, temporal pattern)
- [x] Cross-cutting concerns mapped (audit trail, failure isolation, extensibility, data sensitivity, timezone)

**Architectural Decisions**
- [x] Critical decisions documented with versions
- [x] Technology stack fully specified (Spark 3.x/Java, FastAPI/Python, React/Vite/TypeScript)
- [x] Integration patterns defined (Postgres-only inter-component, REST API for dashboard)
- [x] Performance considerations addressed (indexes, batch writes, no pagination, React Query)

**Implementation Patterns**
- [x] Naming conventions established (DB, API, per-runtime code)
- [x] Structure patterns defined (directory trees per component)
- [x] API patterns specified (endpoints, error format, snake_case JSON)
- [x] Process patterns documented (error handling, logging, loading states)

**Project Structure**
- [x] Complete directory structure defined per component
- [x] Component boundaries established (self-contained, split-ready)
- [x] Integration points mapped (Postgres boundaries, schema ownership)
- [x] Requirements to structure mapping complete (all FRs to files)

### Architecture Readiness Assessment

**Overall Status:** READY FOR IMPLEMENTATION

**Confidence Level:** High

**Key Strengths:**
- Clean unidirectional dependency chain — no circular dependencies
- Postgres as sole integration layer — simple, proven, debuggable
- Self-contained components — split into separate repos without changes
- DB-enforced data integrity — sentinel-based uniqueness, active-record views
- Two-layer failure isolation — robust for batch processing at scale
- Check extensibility via strategy pattern — Tier 2-4 additions require zero serve/dashboard changes
- Data sensitivity enforced by type system — not just convention

**Areas for Future Enhancement:**
- Materialized views if query performance degrades at scale
- API pagination if LOB dataset counts exceed hundreds
- Admin UI for check_config and dataset_enrichment tables
- E2E testing (Playwright) for dashboard
- Dark mode

### Implementation Handoff

**AI Agent Guidelines:**
- Follow all architectural decisions exactly as documented
- Use implementation patterns consistently across all components
- Respect project structure and boundaries
- Query active-record views, never raw tables, in the serve layer
- Use `EXPIRY_SENTINEL` constant, never hardcode the sentinel value
- Include `run_id` in log messages where available
- Place files in documented directory structure — don't invent new directories
- Test data fixtures go in each component's `tests/fixtures/`

**Implementation Sequence:**
1. Postgres schema DDL + active-record views (dqs-serve owns)
2. dqs-spark DqCheck interface + Tier 1 check refactoring + BatchWriter
3. dqs-orchestrator CLI + spark-submit runner + summary email
4. dqs-serve API endpoints + reference data cache + MCP tools
5. dqs-dashboard pages + components + React Query integration
