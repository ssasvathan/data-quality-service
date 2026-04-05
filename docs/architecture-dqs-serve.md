# Architecture — dqs-serve

_Python 3.12+ / FastAPI / SQLAlchemy 2.0 / FastMCP — API layer and schema DDL owner._

---

## Overview

dqs-serve is the API layer and **single owner of the PostgreSQL schema DDL**. It provides REST endpoints for the dashboard, MCP tools for LLM integration, and a ReferenceDataService for LOB lookup resolution. It is read-only — it never writes DQ metrics.

**Entry point:** `uvicorn src.serve.main:app`

---

## Module Structure

```
src/serve/
├── main.py              # FastAPI app, lifespan, error handlers, /health
├── dependencies.py      # get_reference_data_service() dependency
├── db/
│   ├── engine.py        # SQLAlchemy engine + SessionLocal factory
│   ├── session.py       # get_db() dependency (yields Session)
│   ├── models.py        # Base, EXPIRY_SENTINEL constant
│   └── views.py         # Active-record view helpers
├── routes/
│   ├── summary.py       # GET /api/summary (7-day fixed window)
│   ├── lobs.py          # GET /api/lobs, /api/lobs/{lob_id}/datasets
│   ├── datasets.py      # GET /api/datasets/{id}, /metrics, /trend
│   ├── search.py        # GET /api/search?q= (ILIKE, top 10, prefix sort)
│   └── executive.py     # GET /api/executive/report
├── services/
│   └── reference_data.py # ReferenceDataService (12h TTL, thread-safe, double-check locking)
├── mcp/
│   └── tools.py         # FastMCP tools (3 LLM-optimized tools)
└── schema/
    ├── ddl.sql          # 7 tables with temporal pattern
    ├── views.sql        # 7 active-record views
    └── fixtures.sql     # Test data (7-day baseline, mixed statuses)
```

---

## API Routes

| Method | Path | Response Model | Description |
|--------|------|----------------|-------------|
| GET | `/health` | `dict` | Infrastructure readiness |
| GET | `/api/summary` | `SummaryResponse` | LOB summary with 7-day trends |
| GET | `/api/lobs` | `list[LobDetail]` | All LOBs with aggregate scores |
| GET | `/api/lobs/{lob_id}/datasets` | `LobDatasetsResponse` | Datasets per LOB with sparklines |
| GET | `/api/datasets/{id}` | `DatasetDetail` | Full dataset metadata |
| GET | `/api/datasets/{id}/metrics` | `DatasetMetricsResponse` | Check results grouped by type |
| GET | `/api/datasets/{id}/trend` | `DatasetTrendResponse` | Score trend over time window |
| GET | `/api/search?q=` | `SearchResponse` | Dataset search (ILIKE, top 10) |
| GET | `/api/executive/report` | `ExecutiveReportResponse` | LOB monthly scores, src system, improvement |

---

## MCP Tools

Mounted at `/mcp/` via FastMCP HTTP transport. Return plain text (LLM-optimized).

| Tool | Parameters | Use Case |
|------|-----------|----------|
| `query_failures` | `time_range="latest"` | "What failed last night?" |
| `query_dataset_trend` | `dataset_name`, `time_range="7d"` | "Show trending for dataset X" |
| `compare_lob_quality` | (none) | "Which LOB has worst quality?" |

---

## Database Layer

**SQLAlchemy 2.0 style:** `db.execute(text(...)).mappings().all()` — never legacy `session.query()`

**Views queried (never raw tables):**
- `v_dq_run_active` — Latest DQ runs
- `v_dq_metric_numeric_active` — Numeric metrics
- `v_dq_metric_detail_active` — JSONB detail metrics
- `v_dq_orchestration_run_active` — Parent path lookups
- `v_lob_lookup_active` — LOB reference data
- `v_check_config_active` — Check configuration
- `v_dataset_enrichment_active` — Enrichment rules

**Key query patterns:**
- ROW_NUMBER() CTE for "latest per dataset_name" (avoids N+1)
- Batched queries with `= ANY(:array)` or `IN` clauses
- `MAX(partition_date) - CAST(:days_back) * INTERVAL '1 day'` for time windows

---

## ReferenceDataService

Singleton on `app.state`, initialized at startup during FastAPI lifespan.

- Queries `v_lob_lookup_active` → caches `lookup_code → LobMapping`
- 12-hour TTL with auto-refresh
- Thread-safe with double-check locking pattern
- Accessed via `Depends(get_reference_data_service)` in routes
- Returns `LobMapping(lob_name, owner, classification)` or `N/A` fallback

---

## Error Handling

- `404`: `{"detail": "...", "error_code": "NOT_FOUND"}` — missing datasets/LOBs
- `503`: `{"detail": "...", "error_code": "SERVICE_UNAVAILABLE"}` — reference data failure
- `500`: `{"detail": "Internal server error", "error_code": "INTERNAL_ERROR"}` — global handler
- Stack traces are **never** returned in error responses

---

## Test Strategy

- **Unit tests** with mock DB session (auto-injected via `override_db_dependency` fixture)
- **Integration tests** against real Postgres (`@pytest.mark.integration`, excluded by default)
- `seeded_client` fixture: DDL → views → fixtures in transaction, committed for app visibility
- `mock_reference_data_service` auto-use fixture injects predictable LOB mappings
- Test directories: `test_routes/`, `test_schema/`, `test_mcp/`, `test_services/`
