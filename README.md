# Data Quality Service

Automated data quality monitoring platform for HDFS datasets. Scans datasets daily, runs configurable quality checks (freshness, volume, schema drift, operational anomalies), computes composite quality scores, and presents results through a web dashboard and MCP tools for LLM integration.

## Architecture

```
                        ┌──────────────┐
                        │     HDFS     │
                        │ Consumer Zone│
                        └──────┬───────┘
                               │ read datasets
                               ▼
┌──────────────────┐    ┌──────────────┐    ┌──────────────┐
│  dqs-orchestrator│───▶│   dqs-spark  │───▶│  PostgreSQL   │
│  Python CLI      │    │  Java/Spark  │    │  15           │
│  spark-submit    │    │  16 DQ checks│    │  7 tables     │
│  run tracking    │───▶│              │    │  7 views      │
│  email alerts    │    └──────────────┘    └──────┬───────┘
└──────────────────┘                               │ read views
                                                   ▼
                                            ┌──────────────┐
                     ┌─────────────────────▶│  dqs-serve   │◀─── MCP Clients
                     │  HTTP /api/*         │  FastAPI     │     (LLM agents)
                     │                      │  9 endpoints │
              ┌──────┴───────┐              │  3 MCP tools │
              │dqs-dashboard │              └──────────────┘
              │ React SPA    │
              │ MUI 7        │
              └──────────────┘
```

Components communicate **only through PostgreSQL** — no direct HTTP/RPC between backends.

## Components

| Component | Stack | Purpose |
|-----------|-------|---------|
| [**dqs-spark**](docs/architecture-dqs-spark.md) | Java 17, Spark 3.5.0 | 16 data quality checks (freshness, volume, schema, ops + Tier 2/3), composite scoring |
| [**dqs-serve**](docs/architecture-dqs-serve.md) | Python 3.12, FastAPI, SQLAlchemy 2.0 | REST API, MCP tools, schema DDL owner |
| [**dqs-orchestrator**](docs/architecture-dqs-orchestrator.md) | Python 3.12, psycopg2 | spark-submit orchestration, run tracking, rerun management, email alerts |
| [**dqs-dashboard**](docs/architecture-dqs-dashboard.md) | React 19, TypeScript 5, Vite 8, MUI 7 | 3-level drill-down SPA (Summary > LOB > Dataset) + executive reporting |

## Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 17+ (21 for Spark tests)
- Python 3.12+ with [uv](https://docs.astral.sh/uv/)
- Node.js 22+

### 1. Start services

```bash
docker-compose up -d    # Postgres :5432, serve :8000, dashboard :5173
```

### 2. Initialize database with schema and test data

```bash
cat dqs-serve/src/serve/schema/ddl.sql \
    dqs-serve/src/serve/schema/views.sql \
    dqs-serve/src/serve/schema/fixtures.sql \
  | docker exec -i $(docker-compose ps -q postgres) psql -U postgres postgres
```

This creates 7 tables, 7 active-record views, and seeds realistic test data (7-day history, mixed pass/warn/fail statuses, multiple LOBs).

### 3. Verify

```bash
curl http://localhost:8000/api/summary   # API responds with fixture data
open http://localhost:5173               # Dashboard with test data
```

See the [Development Guide](docs/development-guide.md) for per-component build/test commands, mock data details, and how to add your own test datasets.

## Build & Test

```bash
# dqs-spark (Java/Maven)
cd dqs-spark && mvn test

# dqs-serve (Python/FastAPI)
cd dqs-serve && uv sync && uv run pytest                  # unit tests
cd dqs-serve && uv run pytest -m integration              # requires Postgres

# dqs-orchestrator (Python CLI)
cd dqs-orchestrator && uv sync && uv run pytest

# dqs-dashboard (React/TypeScript)
cd dqs-dashboard && npm install && npm test

# Workspace acceptance tests
uv run pytest tests/acceptance/
```

## Key Design Decisions

- **Temporal soft-delete** — All tables use `create_date` + `expiry_date` with a sentinel value (`9999-12-31 23:59:59`). Active-record views filter automatically. Full audit history preserved.
- **Strategy pattern for checks** — Adding a new DQ check = new Java class + factory registration + `check_config` row. Zero changes to serve/API/dashboard.
- **Per-dataset failure isolation** — One bad dataset never crashes the Spark job. One failed parent path never halts the orchestrator.
- **Schema DDL ownership** — dqs-serve is the single source of truth for all Postgres schema. All migration changes go through `dqs-serve/src/serve/schema/`.

## Documentation

Comprehensive project documentation lives in [`docs/`](docs/index.md):

| Document | What it covers |
|----------|---------------|
| [**Project Overview**](docs/project-overview.md) | Architecture summary, tech stack, component responsibilities |
| [**Source Tree**](docs/source-tree-analysis.md) | Annotated directory structure for all 4 components |
| [**Data Models**](docs/data-models.md) | ER diagram, 7 tables, temporal pattern, test fixtures |
| [**API Contracts**](docs/api-contracts.md) | 9 REST endpoints + 3 MCP tools with request/response schemas |
| [**Integration Architecture**](docs/integration-architecture.md) | Data flow diagram, 7 integration points, dependency chain |
| [**Development Guide**](docs/development-guide.md) | Setup, build, test, mock data, DB procedures |

Per-component architecture docs:
[dqs-spark](docs/architecture-dqs-spark.md) |
[dqs-serve](docs/architecture-dqs-serve.md) |
[dqs-orchestrator](docs/architecture-dqs-orchestrator.md) |
[dqs-dashboard](docs/architecture-dqs-dashboard.md)

## Project Structure

```
data-quality-service/
├── dqs-spark/           # Java/Maven — Spark data quality checks
├── dqs-serve/           # Python/FastAPI — API + MCP + schema DDL
├── dqs-orchestrator/    # Python CLI — spark-submit orchestration
├── dqs-dashboard/       # React/TypeScript — SPA frontend
├── tests/               # Workspace-level acceptance tests
├── docs/                # Generated project documentation
├── docker-compose.yml   # Postgres + serve + dashboard
└── _bmad-output/        # Planning artifacts (PRD, architecture, epics, UX spec)
```
