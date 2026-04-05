# Data Quality Service — Documentation Index

_Primary entry point for AI-assisted development and project navigation._

---

## Project Overview

- **Type:** Monorepo with 4 self-contained components
- **Primary Languages:** Java 17, Python 3.12+, TypeScript 5
- **Architecture:** Shared-nothing; components communicate only through PostgreSQL
- **Database:** PostgreSQL 15 with temporal soft-delete pattern (7 tables, 7 active-record views)

### Quick Reference

#### dqs-spark (backend)
- **Tech Stack:** Java 17, Spark 3.5.0, PostgreSQL JDBC, JUnit 5
- **Entry Point:** `DqsJob.main()` via `spark-submit`
- **Root:** `dqs-spark/`

#### dqs-serve (backend)
- **Tech Stack:** Python 3.12+, FastAPI, SQLAlchemy 2.0, FastMCP, Pydantic 2
- **Entry Point:** `uvicorn src.serve.main:app`
- **Root:** `dqs-serve/`

#### dqs-orchestrator (backend)
- **Tech Stack:** Python 3.12+, psycopg2, PyYAML
- **Entry Point:** `python -m orchestrator.cli`
- **Root:** `dqs-orchestrator/`

#### dqs-dashboard (web)
- **Tech Stack:** React 19, TypeScript 5, Vite 8, MUI 7, TanStack Query, Recharts
- **Entry Point:** `src/main.tsx`
- **Root:** `dqs-dashboard/`

---

## Generated Documentation

### Core

- [Project Overview](./project-overview.md) — Executive summary, architecture, tech stack
- [Source Tree Analysis](./source-tree-analysis.md) — Annotated directory structure with critical folders
- [Integration Architecture](./integration-architecture.md) — How components communicate (data flow diagram)
- [Data Models](./data-models.md) — Database schema, ER diagram, temporal pattern, fixtures
- [API Contracts](./api-contracts.md) — REST endpoints, MCP tools, request/response schemas
- [Development Guide](./development-guide.md) — Setup, build, test, mock data, DB history build-out

### Per-Component Architecture

- [Architecture — dqs-spark](./architecture-dqs-spark.md) — Check engine, strategy pattern, 16 check types
- [Architecture — dqs-serve](./architecture-dqs-serve.md) — API routes, MCP tools, schema DDL ownership
- [Architecture — dqs-orchestrator](./architecture-dqs-orchestrator.md) — spark-submit, run tracking, rerun, email
- [Architecture — dqs-dashboard](./architecture-dqs-dashboard.md) — React SPA, routing, theme, accessibility

### Metadata

- [Project Parts](./project-parts.json) — Machine-readable component and integration metadata

---

## Existing Documentation

### Planning Artifacts (`_bmad-output/`)

- [Project Context (AI Rules)](../_bmad-output/project-context.md) — 52 rules for AI agents
- [PRD](../_bmad-output/planning-artifacts/prd.md) — Product Requirements Document
- [Architecture Design](../_bmad-output/planning-artifacts/architecture.md) — Original architecture decisions
- [UX Design Specification](../_bmad-output/planning-artifacts/ux-design-specification/index.md) — 12-part UX spec
- [Epic Overview](../_bmad-output/planning-artifacts/epics/overview.md) — 7 epics with stories
- [Epic List](../_bmad-output/planning-artifacts/epics/epic-list.md) — All epics at a glance

### Implementation Artifacts

- Story specs: `_bmad-output/implementation-artifacts/` (~30+ stories)
- Test artifacts: `_bmad-output/test-artifacts/` (ATDD checklists, traceability reports)

---

## Getting Started

### For New Developers

1. Read the [Development Guide](./development-guide.md) for setup instructions
2. Run `docker-compose up -d` to start Postgres, serve, and dashboard
3. Initialize the database with schema + fixtures (see Development Guide)
4. Open `http://localhost:5173` to see the dashboard with test data

### For AI Agents

1. Read `_bmad-output/project-context.md` — **mandatory** before any code changes
2. Reference this index for navigating the project documentation
3. Schema changes go through `dqs-serve/src/serve/schema/` (DDL owner)
4. New checks require: Java class + factory registration + `check_config` row — zero API/dashboard changes

### For Feature Planning

1. Full-stack features: [Integration Architecture](./integration-architecture.md) + both relevant component architectures
2. API-only features: [Architecture — dqs-serve](./architecture-dqs-serve.md) + [API Contracts](./api-contracts.md)
3. Dashboard-only features: [Architecture — dqs-dashboard](./architecture-dqs-dashboard.md)
4. New DQ checks: [Architecture — dqs-spark](./architecture-dqs-spark.md)

---

_Generated: 2026-04-05 | Scan level: Deep | Mode: Initial scan_
