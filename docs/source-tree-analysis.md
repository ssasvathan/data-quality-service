# Source Tree Analysis

_Annotated directory structure for the Data Quality Service monorepo._

---

## Repository Root

```
data-quality-service/
├── dqs-spark/              # Java/Maven — Spark data quality checks (Tier 1-3)
├── dqs-serve/              # Python/FastAPI — API + MCP + schema DDL owner
├── dqs-orchestrator/       # Python CLI — spark-submit runner + email notifications
├── dqs-dashboard/          # React/TypeScript — SPA frontend
├── tests/                  # Workspace-level acceptance tests (Story 1.1)
│   └── acceptance/
│       ├── conftest.py                           # project_root, component_paths fixtures
│       ├── test_story_1_1_project_structure.py   # Validates 4-component structure
│       └── test_story_1_1_docker_compose_startup.py
├── docker-compose.yml      # Postgres 15 + dqs-serve + dqs-dashboard
├── pyproject.toml          # Workspace dev tooling (ruff, pytest markers)
├── pytest.ini              # Root pytest config (integration marker)
├── CLAUDE.md               # AI agent instructions
├── _bmad-output/           # Planning artifacts (PRD, architecture, epics, UX spec)
├── spark-job/              # ⚠ ARCHIVED — prototype Spark code
├── serve-api/              # ⚠ ARCHIVED — prototype API code
└── db/                     # ⚠ ARCHIVED — outdated schema init script
```

---

## dqs-spark (Java 17 / Maven / Spark 3.5.0)

Entry point: `DqsJob.main()` via `spark-submit`

```
dqs-spark/
├── pom.xml                             # Maven build; Spark 3.5.0 (provided scope)
├── config/
│   └── application.properties          # JDBC connection, Spark app settings
└── src/
    ├── main/java/com/bank/dqs/
    │   ├── DqsJob.java                 # ★ Main entry point — orchestrates scan → check → write
    │   ├── model/
    │   │   ├── DatasetContext.java      # Immutable dataset context (name, df, format, partition)
    │   │   ├── DqMetric.java           # Marker interface for all metric types
    │   │   ├── MetricNumeric.java      # Numeric metric (check_type, metric_name, value)
    │   │   ├── MetricDetail.java       # Structured JSON metric (check_type, detail_type, JSONB)
    │   │   └── DqsConstants.java       # EXPIRY_SENTINEL, shared constants
    │   ├── checks/                     # ★ Strategy pattern — 16 check implementations
    │   │   ├── DqCheck.java            # Strategy interface: execute(DatasetContext) → List<DqMetric>
    │   │   ├── CheckFactory.java       # Registry; queries check_config with LIKE pattern matching
    │   │   ├── FreshnessCheck.java     # Tier 1 — hours since latest source_event_timestamp
    │   │   ├── VolumeCheck.java        # Tier 1 — row count deviation from baseline
    │   │   ├── SchemaCheck.java        # Tier 1 — schema hash + field drift detection
    │   │   ├── OpsCheck.java           # Tier 1 — null rate + empty string rate per column
    │   │   ├── DqsScoreCheck.java      # Composite — weighted score from Tier 1 (runs last)
    │   │   ├── SlaCountdownCheck.java          # Tier 2 — hours until SLA breach
    │   │   ├── ZeroRowCheck.java               # Tier 2 — empty dataset detection
    │   │   ├── BreakingChangeCheck.java        # Tier 2 — field removal detection
    │   │   ├── DistributionCheck.java          # Tier 2 — value distribution anomalies
    │   │   ├── TimestampSanityCheck.java       # Tier 2 — timestamp consistency
    │   │   ├── ClassificationWeightedCheck.java # Tier 3 — classification-based scoring
    │   │   ├── SourceSystemHealthCheck.java    # Tier 3 — source system KPI tracking
    │   │   ├── CorrelationCheck.java           # Tier 3 — cross-dataset correlation
    │   │   ├── InferredSlaCheck.java           # Tier 3 — SLA prediction from history
    │   │   ├── LineageCheck.java               # Tier 3 — data lineage tracking
    │   │   ├── OrphanDetectionCheck.java       # Tier 3 — orphaned record detection
    │   │   └── CrossDestinationCheck.java      # Tier 3 — multi-destination consistency
    │   ├── scanner/
    │   │   ├── PathScanner.java        # Interface for dataset discovery
    │   │   ├── ConsumerZoneScanner.java # HDFS scanner (partition_date/src_sys_nm structure)
    │   │   └── EnrichmentResolver.java # Resolves src_sys_nm → lookup_code via DB
    │   ├── reader/
    │   │   └── DatasetReader.java      # Reads Avro/Parquet from HDFS into Spark DataFrames
    │   └── writer/
    │       └── BatchWriter.java        # JDBC batch writer → dq_run + metrics (single transaction)
    └── test/java/com/bank/dqs/
        ├── DqsJobArgParserTest.java
        ├── DqsJobConfigLoadingTest.java
        ├── model/
        │   ├── DatasetContextTest.java
        │   └── MetricModelTest.java
        ├── checks/                     # One test class per check (H2 + Spark fixtures)
        │   ├── CheckFactoryTest.java
        │   ├── FreshnessCheckTest.java
        │   ├── VolumeCheckTest.java
        │   ├── SchemaCheckTest.java
        │   ├── OpsCheckTest.java
        │   ├── DqsScoreCheckTest.java
        │   └── ... (16 total test classes)
        ├── scanner/
        │   ├── ConsumerZoneScannerTest.java
        │   ├── ConsumerZoneScannerEnrichmentTest.java
        │   └── EnrichmentResolverTest.java
        ├── reader/
        │   └── DatasetReaderTest.java
        └── writer/
            └── BatchWriterTest.java
```

---

## dqs-serve (Python 3.12+ / FastAPI / SQLAlchemy 2.0 / FastMCP)

Entry point: `uvicorn src.serve.main:app`

```
dqs-serve/
├── pyproject.toml                  # Dependencies (fastapi, sqlalchemy, fastmcp, pydantic)
├── Dockerfile                      # Python 3.12-slim, uv sync, uvicorn
├── config/
│   └── serve.yaml                  # DB pool, server host/port
├── conftest.py                     # Adds src/ to sys.path for pytest
├── main.py                         # Stub (unused; real entry is src/serve/main.py)
└── src/serve/
    ├── main.py                     # ★ FastAPI app, lifespan, error handlers, /health
    ├── dependencies.py             # get_reference_data_service() dependency
    ├── db/
    │   ├── engine.py               # SQLAlchemy engine + SessionLocal factory
    │   ├── session.py              # get_db() FastAPI dependency (yields Session)
    │   ├── models.py               # Base, EXPIRY_SENTINEL constant
    │   └── views.py                # Active-record view helpers
    ├── routes/                     # ★ API route handlers
    │   ├── summary.py              # GET /api/summary
    │   ├── lobs.py                 # GET /api/lobs, /api/lobs/{lob_id}/datasets
    │   ├── datasets.py             # GET /api/datasets/{id}, /metrics, /trend
    │   ├── search.py               # GET /api/search?q=
    │   └── executive.py            # GET /api/executive/report
    ├── services/
    │   └── reference_data.py       # ReferenceDataService — LOB lookup cache (12h TTL)
    ├── mcp/
    │   └── tools.py                # FastMCP tools (query_failures, query_dataset_trend, compare_lob_quality)
    └── schema/                     # ★ DDL owner — all Postgres schema lives here
        ├── ddl.sql                 # 7 tables with temporal pattern
        ├── views.sql               # 7 active-record views (v_*_active)
        └── fixtures.sql            # Test data (7-day baseline, mixed statuses, LOB lookups)
tests/
├── conftest.py                     # ★ Test fixtures: client, db_conn, seeded_client, mock overrides
├── test_routes/
│   ├── test_summary.py
│   ├── test_lobs.py
│   ├── test_datasets.py
│   ├── test_search.py
│   └── test_executive.py
├── test_mcp/
│   └── test_tools.py
├── test_schema/
│   ├── test_dq_run_schema.py
│   ├── test_metric_schema.py
│   ├── test_orchestration_schema.py
│   ├── test_config_enrichment_schema.py
│   ├── test_views_indexes.py
│   └── test_fixtures.py
└── test_services/
    └── test_reference_data.py
```

---

## dqs-orchestrator (Python 3.12+ / CLI)

Entry point: `python -m orchestrator.cli`

```
dqs-orchestrator/
├── pyproject.toml                  # Dependencies (psycopg2-binary, pyyaml)
├── main.py                         # Stub entry
├── config/
│   ├── orchestrator.yaml           # Spark submit, DB, email settings
│   └── parent_paths.yaml           # Parent paths for DQ job execution
└── src/orchestrator/
    ├── __init__.py
    ├── cli.py                      # ★ CLI entry: parse_args, load config, orchestrate
    ├── runner.py                   # spark-submit execution + per-path failure isolation
    ├── db.py                       # psycopg2 helpers (create/finalize runs, expire metrics)
    ├── email.py                    # Summary email composition + SMTP delivery
    └── models.py                   # JobResult, RunSummary dataclasses
tests/
├── test_cli.py                     # CLI args, config loading, orchestration flow
├── test_runner.py                  # spark-submit command, timeout, failure isolation
├── test_db.py                      # DB operations (create/finalize/expire runs)
└── test_email.py                   # Email composition, SMTP delivery
```

---

## dqs-dashboard (React 19 / TypeScript 5 / Vite 8 / MUI 7)

Entry point: `src/main.tsx`

```
dqs-dashboard/
├── package.json                    # Dependencies (React 19, MUI 7, TanStack Query, Recharts)
├── Dockerfile                      # Node 22-slim, npm ci, dev server
├── vite.config.ts                  # Port 5173, /api proxy → localhost:8000, Vitest config
├── tsconfig.json                   # Strict TypeScript
├── eslint.config.js                # React hooks + refresh rules
├── index.html                      # SPA shell (<div id="root">)
├── public/
│   ├── favicon.svg
│   └── icons.svg
└── src/
    ├── main.tsx                    # ★ React 19 root, axe-core accessibility auditing
    ├── App.tsx                     # QueryClient + Theme + Router + TimeRange + Layout
    ├── theme.ts                    # MUI 7 custom theme (neutral palette, semantic colors)
    ├── index.css                   # Global reset styles
    ├── api/
    │   ├── client.ts               # apiFetch wrapper (typed fetch)
    │   ├── queries.ts              # ★ TanStack Query hooks (useSummary, useLobs, etc.)
    │   └── types.ts                # API response type definitions (snake_case)
    ├── context/
    │   └── TimeRangeContext.tsx     # Global time range state (7d/30d/90d)
    ├── components/
    │   ├── DqsScoreChip.tsx        # Score badge (lg/md/sm, trend arrow, semantic color)
    │   ├── TrendSparkline.tsx      # Recharts LineChart (lg/md/mini, optional baseline)
    │   ├── DatasetCard.tsx         # LOB summary card (score, trend, status counts)
    │   ├── DatasetInfoPanel.tsx    # Dataset metadata panel (dl/dt/dd, copyable HDFS path)
    │   └── index.ts                # Barrel export
    ├── layouts/
    │   └── AppLayout.tsx           # Fixed header, breadcrumbs, search, time range toggle
    ├── pages/
    │   ├── SummaryPage.tsx         # Landing — LOB grid with aggregate stats
    │   ├── LobDetailPage.tsx       # LOB drill-down — MUI X DataGrid per dataset
    │   ├── DatasetDetailPage.tsx   # Dataset detail — master-detail split layout
    │   └── ExecReportPage.tsx      # Executive report — scorecard, improvement, source systems
    └── assets/
        ├── hero.png
        ├── react.svg
        └── vite.svg
tests/
├── setup.ts                        # jest-dom matchers
├── api/
│   └── useSearch.test.ts
├── components/
│   ├── DatasetCard.test.tsx
│   ├── DqsScoreChip.test.tsx
│   ├── DatasetInfoPanel.test.tsx
│   ├── TrendSparkline.test.tsx
│   └── theme.test.ts
├── context/
│   └── TimeRangeContext.test.tsx
├── layouts/
│   ├── AppLayout.test.tsx
│   └── GlobalSearch.test.tsx
└── pages/
    ├── SummaryPage.test.tsx
    ├── LobDetailPage.test.tsx
    ├── DatasetDetailPage.test.tsx
    └── ExecReportPage.test.tsx
```

---

## Critical Folders Summary

| Folder | Purpose | Owner |
|--------|---------|-------|
| `dqs-serve/src/serve/schema/` | Postgres DDL, views, fixtures — **single source of truth** for DB schema | dqs-serve |
| `dqs-spark/src/main/java/.../checks/` | All 16 DQ check implementations + factory + composite score | dqs-spark |
| `dqs-spark/src/main/java/.../writer/` | JDBC batch writer — only component that writes DQ metrics to Postgres | dqs-spark |
| `dqs-orchestrator/src/orchestrator/` | CLI orchestration — spark-submit, run tracking, rerun, email | dqs-orchestrator |
| `dqs-dashboard/src/api/` | API client, TanStack Query hooks, type definitions | dqs-dashboard |
| `dqs-dashboard/src/pages/` | 4 page components (Summary, LOB Detail, Dataset Detail, Exec Report) | dqs-dashboard |
| `dqs-serve/tests/conftest.py` | Test infrastructure — mock DB, seeded_client, real Postgres fixtures | dqs-serve |
