---
project_name: 'data-quality-service'
user_name: 'Sas'
date: '2026-04-03'
sections_completed:
  ['technology_stack', 'language_rules', 'framework_rules', 'testing_rules', 'quality_rules', 'workflow_rules', 'anti_patterns']
status: 'complete'
rule_count: 52
optimized_for_llm: true
---

# Project Context for AI Agents

_This file contains critical rules and patterns that AI agents must follow when implementing code in this project. Focus on unobvious details that agents might otherwise miss._

---

## Technology Stack & Versions

### Multi-Component Architecture

This is a 5-component data quality platform. Each component is **fully self-contained** — own config, dependencies, build. Designed to split into independent repos without changes.

| Component | Runtime | Key Dependencies |
|---|---|---|
| **dqs-spark** | Java 17 (source), Spark 3.5.0 (provided) | PostgreSQL JDBC 42.7.2, Jackson 2.15.4, JUnit 5.10.2 |
| **dqs-serve** | Python 3.12+, FastAPI 0.135.1 | SQLAlchemy 2.0.48, FastMCP 3.2.0, Pydantic 2 |
| **dqs-orchestrator** | Python 3.12+ | psycopg2-binary, pyyaml |
| **dqs-dashboard** | TypeScript 5.x, React 19, Vite 8.0.3 | MUI 7.3.9, React Router 7.13.2, TanStack Query, Recharts 3.8.1 |
| **Infrastructure** | Postgres 15, Docker Compose | Single instance, no HA |

### Version Constraints

- **Spark tests require Java 21 JVM** — Java 25 removed `Subject.getSubject()` used by Hadoop UGI. Enforced via Maven Surefire `<jvm>` config pointing to `/usr/lib/jvm/java-21-openjdk-amd64/bin/java`
- **Spark 3.5.0 needs `--add-opens` JVM flags** for module access (12 flags in pom.xml Surefire config)
- **Python components use `uv`** for dependency management, not pip
- **Spark is `provided` scope** — never bundle Spark JARs into the fat JAR

---

## Critical Implementation Rules

### Language-Specific Rules

**Java (dqs-spark):**
- Use **try-with-resources** for all JDBC connections, statements, result sets — no manual close
- Use **PreparedStatement** with `?` placeholders for all queries — never string concatenation
- Use **batch operations** (`addBatch()` + `executeBatch()`) for bulk inserts — single transaction per parent path
- Declare `throws SQLException` on method signatures — prefer checked exception propagation over catch-and-swallow
- Constructor validation with `IllegalArgumentException` for invalid arguments
- Use Jackson annotations (`@JsonCreator`, `@JsonProperty`, `@JsonIgnoreProperties`) for config deserialization
- Static imports for Spark SQL functions: `import static org.apache.spark.sql.functions.*`
- Use full generic types: `List<CheckResult>`, `Dataset<Row>` — never raw types

**Python (dqs-serve, dqs-orchestrator):**
- Use **type hints** on all new code — function parameters and return types
- Use **Pydantic 2** models for API request/response validation in dqs-serve
- SQLAlchemy 2.0 style — use `Session` context managers, not legacy `session.query()`
- Environment variable configuration: `os.getenv("KEY", "default")` pattern
- Relative imports within a package (`.db`, `.models`), absolute for external

**TypeScript (dqs-dashboard):**
- Strict mode TypeScript — no `any` types
- Define API response types in `api/types.ts` — never inline type assertions
- Use `interface` for API contracts, `type` for unions/intersections

### Framework-Specific Rules

**FastAPI (dqs-serve):**
- All API responses use **snake_case** JSON keys — matches Postgres columns and Python conventions
- Error responses: `{"detail": "message", "error_code": "NOT_FOUND"}` — FastAPI default shape
- Never return stack traces in error responses
- Query active-record views (`v_*_active`), **never raw tables** with manual expiry filters
- MCP tools wrap the same API endpoint logic — same data, different interface

**Spark (dqs-spark):**
- **DqCheck strategy pattern interface** — all checks implement a common contract
- Checks output to generic metric tables (`dq_metric_numeric`, `dq_metric_detail`) — detail metrics contain field names, **never source data values** (data sensitivity boundary)
- Adding a new check = new Java class + factory registration + `check_config` row — zero changes to serve/API/dashboard
- DQS Score is a **weighted composite computed in Spark** — the only component that knows check types
- Per-dataset failure isolation: catch exceptions per dataset, log error, record in results, continue to next dataset. **Never let one dataset crash the JVM.**

**React (dqs-dashboard):**
- **TanStack Query (React Query)** for all API data fetching — no `useEffect` + `fetch`
- `stale-while-revalidate` pattern — global time range change invalidates all queries
- **Skeleton screens** matching target layout per UX spec — no spinning loaders
- React Query `isLoading` for skeletons, `isFetching` for stale-while-revalidate indicators
- Component-level error display ("Failed to load") — **never full-page crashes** for partial failures
- MUI 7 custom theme with muted palette and semantic colors per UX spec

**Orchestrator (dqs-orchestrator):**
- Python CLI managing 5-10 `spark-submit` invocations (one per parent path)
- Per-parent-path failure isolation — failed paths don't halt others
- Summary email composed by orchestrator: categorizes parent path failures vs dataset failures with actionable rerun commands
- Reads parent paths from `config/parent_paths.yaml`
- Dataset-level rerun via `--datasets` filter parameter

### Temporal Data Pattern (ALL COMPONENTS)

This is the most critical cross-cutting pattern:

- **All tables** use `create_date` + `expiry_date` as `TIMESTAMP` type columns
- **Active records:** `expiry_date = '9999-12-31 23:59:59'` (sentinel value)
- **Use the `EXPIRY_SENTINEL` constant** — defined independently in each runtime. **NEVER hardcode** `9999-12-31 23:59:59` inline
- **Composite unique constraints** on natural business keys + `expiry_date` — DB-enforced, no duplicate active records
- **Active-record views** (`v_*_active`) filter on sentinel — all serve-layer queries go through these views
- **Soft-delete** = update `expiry_date` from sentinel to current timestamp, then insert new record with sentinel
- **Timezone:** All DQS operational timestamps are **Eastern time natively**. No conversion layer. Freshness check reads UTC `source_event_timestamp` from HDFS, computes staleness metric, stores only the metric.

### Testing Rules

**dqs-spark (JUnit 5):**
- Tests in `src/test/java/` mirroring `src/main/java/` (Maven standard)
- Use **H2 in-memory database** for `MetadataRepository`/writer tests — SQL must be compatible with both H2 and Postgres
- Test naming: `<action><expectation>` (e.g., `volumeCheckFlagsAnomaliesOutsideStdDev`)
- Lifecycle: `@BeforeAll`/`@AfterAll` for SparkSession, `@BeforeEach`/`@AfterEach` for test data
- Manual DataFrame creation with `RowFactory` for test fixtures

**dqs-serve (pytest):**
- Tests in top-level `tests/` with subdirectories (`test_routes/`, `test_services/`)
- **Test against real Postgres** — temporal pattern and active-record views need real DB validation
- Use `conftest.py` for shared fixtures (DB connections, test client)
- FastAPI `TestClient` for route testing

**dqs-orchestrator (pytest):**
- Tests in top-level `tests/` directory
- Mock `spark-submit` for unit tests — don't require Spark cluster

**dqs-dashboard (Vitest):**
- Tests in top-level `tests/` by type
- Component tests with React Testing Library
- No E2E tests for MVP (deferred)

### Code Quality & Style Rules

**Naming Conventions per Runtime:**

| Element | dqs-spark (Java) | dqs-serve/orchestrator (Python) | dqs-dashboard (TypeScript) |
|---|---|---|---|
| Files | PascalCase.java | snake_case.py | PascalCase.tsx (components), camelCase.ts (utils) |
| Classes | PascalCase | PascalCase | PascalCase |
| Methods/functions | camelCase | snake_case | camelCase |
| Variables | camelCase | snake_case | camelCase |
| Constants | UPPER_SNAKE | UPPER_SNAKE | UPPER_SNAKE |
| Packages/modules | lowercase.dot.separated | snake_case | kebab-case (dirs) |

**Database Naming:**
- Tables: `snake_case`, singular (`dq_run`, `check_config`)
- Columns: `snake_case` (`dataset_name`, `partition_date`)
- Primary keys: `id` (serial/bigserial)
- Foreign keys: `{referenced_table}_id`
- Indexes: `idx_{table}_{columns}`
- Unique constraints: `uq_{table}_{columns}`
- Views: `v_{table}_active`

**API Endpoint Naming:**
- Plural nouns for collections: `/api/datasets`, `/api/lobs`
- Nested drill-down: `/api/lobs/{lob_id}/datasets`
- All params snake_case: `?time_range=30d&check_type=freshness`

**Directory Structure — Do NOT invent new top-level directories:**

```
dqs-spark/     → Java/Maven, Spark checks + HDFS scanner + batch writer
dqs-serve/     → Python/FastAPI, API + MCP + schema DDL owner
dqs-orchestrator/ → Python CLI, spark-submit runner + email
dqs-dashboard/ → React/TypeScript SPA
```

Each component has: own `config/` directory, own test directory, own build config. **No shared config files.**

### Development Workflow Rules

- **Postgres schema DDL is owned by dqs-serve** — all schema changes go there
- **Components communicate only through Postgres** — no direct HTTP/RPC between backend components
- **docker-compose** runs: Postgres (port 5433), serve (port 8000), dashboard dev server (port 5173)
- dqs-spark and dqs-orchestrator run **outside** docker-compose (Spark on cluster, orchestrator on scheduling server)
- **Implementation sequence:** Schema DDL -> Spark checks -> Orchestrator -> Serve API -> MCP -> Dashboard
- **Unidirectional dependency chain:** Schema -> Spark+Orchestrator -> Serve -> Dashboard+MCP. No circular dependencies.
- **Logging:** Include `run_id` in log messages where available. Levels: ERROR (needs attention), WARN (unexpected but not fatal), INFO (operational milestones), DEBUG (diagnostic)

### Critical Don't-Miss Rules

**Anti-Patterns — NEVER do these:**
- Never query raw tables in the serve layer — always use `v_*_active` views
- Never hardcode the sentinel timestamp `9999-12-31 23:59:59` — use `EXPIRY_SENTINEL` constant
- Never add check-type-specific logic to serve/API/dashboard — only dqs-spark knows check types
- Never let one dataset failure crash the entire Spark job — catch and continue
- Never return raw stack traces from API endpoints
- Never use `useEffect` + `fetch` in the dashboard — use TanStack Query
- Never use spinning loaders — skeletons only
- Never create shared config files across components — duplicate DB connection config per component
- Never persist raw PII/PCI data — checks output aggregate metrics only via `DqMetric` interface contract
- Never use `any` type in TypeScript

**Brownfield Context:**
- Existing `spark-job/` and `serve-api/` directories are **prototype code** (~15-20% implementation)
- Architecture calls for new directories: `dqs-spark/`, `dqs-serve/`, `dqs-orchestrator/`, `dqs-dashboard/`
- ~5-10% of prototype code is reusable; ~80% of domain knowledge is reusable
- Old directories to be archived after rebuild

**Cross-Runtime Consistency:**
- Sentinel timestamp constant and temporal pattern logic **must be identical** in Java and Python
- snake_case flows: Postgres columns -> Python variables -> API JSON keys -> Dashboard consumes as-is
- `run_id` correlation across all components for log tracing

---

## Usage Guidelines

**For AI Agents:**
- Read this file before implementing any code
- Follow ALL rules exactly as documented
- When in doubt, prefer the more restrictive option
- Reference the architecture doc at `_bmad-output/planning-artifacts/architecture.md` for detailed structural decisions
- Reference the PRD at `_bmad-output/planning-artifacts/prd.md` for functional requirements
- Reference the UX spec at `_bmad-output/planning-artifacts/ux-design-specification/` for dashboard design

**For Humans:**
- Keep this file lean and focused on agent needs
- Update when technology stack changes
- Review quarterly for outdated rules
- Remove rules that become obvious over time

Last Updated: 2026-04-03
