---
stepsCompleted: [1]
inputDocuments:
  - artifact/dqs-brainstorm.md
  - docs/superpowers/specs/2026-03-31-dqs-architecture-design.md
session_topic: 'Gap analysis and requirements finalization for DQS -- data quality checks on HDFS data with reference data lookups, targeting enterprise-grade implementation'
session_goals: 'Identify gaps between brainstorm vision and current implementation; finalize scope for PRD; define reference data API scaffold; prioritize Tier 2+ checks; design mock HDFS test dataset for E2E validation; produce scope boundary suitable for BMad PRD/Architecture/Epics workflow'
selected_approach: 'ai-recommended'
techniques_used: ['question-storming', 'six-thinking-hats', 'constraint-mapping']
ideas_generated: []
context_file: ''
---

# Brainstorming Session Results

**Facilitator:** Sas
**Date:** 2026-04-02

## Session Overview

**Topic:** Gap analysis and requirements finalization for Data Quality Service (DQS) -- a bank's data quality platform that reads HDFS files (Avro/Parquet) to compute quality checks, stores results in Postgres, and serves them via FastAPI REST + MCP + executive dashboard.

**Goals:**
- Identify gaps between the original brainstorm (30+ check ideas across 4 tiers) and the half-built implementation
- Finalize requirements scope for a formal PRD
- Define reference data API scaffold (LOB, owners, classification, destinations from existing dataset registration service)
- Prioritize Tier 2+ checks and decide scope boundary
- Produce enterprise-grade specification suitable for BMad PRD → Architecture → Epics workflow

### Context Guidance

**Existing Implementation:**
- DB schema: Complete (4 tables)
- Spark Job (Java/Maven): 5 Tier 1 checks working (Volume #3, Schema #5, Freshness #1, Ops #24, DQS Score #25)
- Serve API (Python/FastAPI): Skeleton only (1 endpoint, MCP stub, no UI)
- Dead code: CheckConfig/RuleConfig models unused; static/index.html referenced but missing
- Reference data lookups (LOB, owners): Not implemented -- need API scaffold
- Mock test data: No representative HDFS test dataset exists for E2E validation

**Key Corrections from User:**
- DQS reads actual data from HDFS (not metadata-only despite brainstorm header)
- Reference data (LOB, owners, classification) must be fetched via API from existing dataset registration service -- build scaffold now, implement later

### Session Setup

Session continues from prior brainstorm that produced 30+ ideas and 4 implementation tiers. This session focuses on gap analysis and scope finalization rather than new ideation.

## Technique Selection

**Approach:** AI-Recommended Techniques
**Analysis Context:** Gap analysis and requirements finalization for half-built DQS, focusing on enterprise-grade scope boundary

**Recommended Techniques:**
- **Question Storming:** Surface blind spots and unanswered questions across all components (checks, reference data, test data, serve layer, compliance)
- **Six Thinking Hats:** Evaluate existing implementation and proposed scope from facts/risks/benefits/creativity/emotions/process perspectives
- **Constraint Mapping:** Separate real constraints from imagined ones and draw the in-scope/out-scope/scaffold-for-later line

**AI Rationale:** This is a refinement session, not greenfield ideation. The 30+ ideas already exist. The gap is in stress-testing those ideas against reality, surfacing what's missing, and drawing a defensible scope boundary for the PRD.

---

## Technique 1: Question Storming

### Domain 1: Spark Job / Check Execution

**Q1: Who decides thresholds?**
- RESOLVED: Platform-driven, adjustable configs per check with sensible defaults.

**Q2: What happens on day 1 (cold start)?**
- RESOLVED: Backfill support. Job accepts optional date param (yyyymmdd). Normal run = current date. On first onboard, run backfill before actual run to seed historical baseline.

**Q3: How does dataset discovery work?**
- RESOLVED: Config provides parent-level paths to scan (e.g. `/prod/abc/data/consumerzone/`). Job scans subdirectories and extracts two variables from path:
  1. **Dataset name** containing a lookup code (e.g. `src_sys_nm=ue90-omni` → `ue90` is the lookup code for reference data)
  2. **Date** for grouping (e.g. `partition_date=20260323`)
- Must support different path structures via extensible interface design.

**Q4: Execution model at scale?**
- RESOLVED: Multiple parent paths configured. Loop triggers Spark jobs on YARN cluster. One path failure must NOT halt entire run. Requires: failure safety, monitoring, alerting, process audit / failure record keeping.

**Q5: Config-driven check rules?**
- RESOLVED: No. Hardcoded rules are fine for v1. Dead code (CheckConfig/RuleConfig) can be removed.

### Original Requirement Context (Captured)

- ~400 GB/day, hundreds of datasets, thousands of consumers
- Platform team with no data domain knowledge -- operates at metadata level
- Avro format: fixed schema with nested stringified JSON in `eventAttribute` map
- Parquet format: schema varies by source system, no schema definitions available
- HDFS path pattern: `/prod/.../src_sys_nm=XYZ/partition_date=YYYYMMDD`
- Spark partitioning job runs at midnight, completes ~3am. DQS runs after.
- Hadoop cluster uses Kerberos auth (keytab-based)
- Local dev: local filesystem paths + local Spark mode (no Hadoop access)

### Domain 2: Reference Data & Dataset Registration

**Q6: What does the registration service return?**
- RESOLVED: It's a DB table (not an API), and it's evolving. Schema not finalized -- scaffold must be flexible.

**Q7: What if lookup code not found?**
- RESOLVED: Populate "N/A". Could surface as a report metric (unresolved datasets count).

**Q8: How stale can reference data be?**
- RESOLVED: Cache at run start, use for entire run. Reference data barely changes.

**Q9: What reference data does Spark need vs. serve layer?**
- RESOLVED: Spark only stores the lookup code. LOB, owner, classification lookups happen in the Python serve layer (UI + MCP only).

**Q10: Where does the reference data scaffold live?**
- RESOLVED: Python client in serve-api only. Additionally, need a local lookup code resolution table for legacy datasets where path doesn't contain the code (e.g. `src_sys_nm=omni` → resolve to `ue90` via DQS-owned mapping table).

### Domain 3: Orchestration, Monitoring & Failure Handling

**Q11: What triggers DQS?**
- RESOLVED: Manual trigger for now. Airflow integration later.

**Q12: Process audit / failure record keeping?**
- RESOLVED: Expand `dq_run` table with orchestration fields. Support rerun tracking with rerun number so audit history is never overwritten.

**Q13: Monitoring and alerting?**
- RESOLVED: Summary email at end of run for SRE team.

**Q14: Retry mechanism?**
- RESOLVED: Manual rerun only. Track rerun number in `dq_run`.

**Q15: Who watches the watcher?**
- DEFERRED: Handle later. Not in v1 scope.

### Cross-Cutting: DB Convention

**All tables** must include:
- `create_date` (format `yyyymmddhhMMssSSSfffffffff`) -- when record was created
- `expiry_date` (same format) -- if active: all 9s (`9999999999999999999999999`). If expired: timestamp when expired.
- This is a soft-delete / temporal pattern. No hard deletes. Current schema needs to be retrofitted.

### Domain 4: Serve Layer (API + UI + MCP)

**Q16: Who is the dashboard for and what does it show?**
- RESOLVED: Data stewards and executives. Must support drill-down from summary → LOB → dataset → individual DQ checks. Full granularity capability required.

**Q17: How does UI resolve LOB/owner for display?**
- RESOLVED: Query-time join against registration DB + legacy mapping table. Cache with twice-daily refresh for performance. Reference data doesn't change much.

**Q18: What MCP tools do LLM consumers need?**
- RESOLVED: Natural language questions like "What failed last night?", "Show me trending for dataset X", "Which LOB has the worst quality score?" MCP tools should map to the same API endpoints the UI uses.

**Q19: Authentication?**
- RESOLVED: No auth for now. Internal network/VPN boundary security.

**Q20: UI technology?**
- RESOLVED: Open to lightweight framework. Not committed to vanilla JS. Vision: performant API that supports a reporting UI with summary view, trending view for execs/data owners by LOB, drill-down to dataset level and individual DQ checks. MCP layer consumes the same API for chatbot answers.

### Domain 5: Test Data & E2E Validation

**Q21: How many source systems to simulate?**
- RESOLVED: 3-5 representative systems covering key variations (Avro vs Parquet, different path structures, different data volumes).

**Q22: How should anomalies be structured?**
- RESOLVED: Mixed into otherwise healthy datasets. No separate "bad" datasets -- anomalies (stale partitions, zero rows, schema drift, high nulls, duplicate keys) should appear naturally within the test data.

**Q23: What does the Avro eventAttribute look like?**
- RESOLVED: Any valid JSON. Must cover all JSON literal types -- strings, numbers, booleans, arrays, objects, nested combinations. Not just string values.

**Q24: How to simulate historical data for baseline checks?**
- RESOLVED: Single date only in file-based test data. History for statistical baselines will be mocked directly in the DB history tables. No need to generate 30 days of partition folders.

**Q25: Include legacy path formats?**
- RESOLVED: Yes. Test data must include at least one legacy-format path (e.g. `src_sys_nm=omni` without embedded lookup code) to exercise the lookup table resolution. Test data should mirror production structure closely to avoid rework.

### Domain 6: Schema, Data Model & DB Design

**Q26: Does `dataset` table need restructuring?**
- RESOLVED: Yes. Spark writes: lookup code, src_sys_nm, parent path, format (Avro/Parquet). Plus mandatory `create_date`/`expiry_date` columns.

**Q27: Legacy lookup table structure?**
- RESOLVED: Simple lookup: `(src_sys_nm_pattern, lookup_code)` + mandatory `create_date`/`expiry_date`. Soft delete only -- no hard deletes. This convention is strictly enforced across ALL tables and ALL components.

**Q28: `dq_run` expansion fields?**
- RESOLVED: Add `parent_path`, `rerun_number`, `error_message`, `datasets_total`, `datasets_passed`, `datasets_failed` + mandatory `create_date`/`expiry_date`.

**Q29: Orchestration-level table?**
- RESOLVED: Yes. New `dq_orchestration_run` table that groups all per-dataset `dq_run` records for a single trigger. Summary email reports from this level. Includes `create_date`/`expiry_date`.

**Q30: Metric snapshot -- single NUMERIC not sufficient for all check types.**
- RESOLVED: Option C -- split into two tables:
  - `dq_metric_numeric`: For numeric metrics (row counts, percentages, scores, stddev, etc.)
  - `dq_metric_detail`: For structured/text results (schema diffs, duplicate key values, change classifications, gap details, etc.)
- Both tables include `create_date`/`expiry_date`.

### Domain 7: Checks -- Scope & Prioritization

**Q31/Q32: What's in v1 scope?**
- RESOLVED: ALL tiers (1-4) are v1 scope. However, implementation is incremental:
  - Early epics: Complete Tier 1 across the full stack (Spark → DB → API → UI → MCP)
  - Later epics: Incrementally add Tier 2, 3, 4 checks
  - **Critical design principle:** The serve layer (API/UI/MCP) must be built in early epics to be extensible enough to support later tier additions without rework.
- Exchange #16 (Duplicate Primary Key) DROPPED -- no primary key info available.

**Q33: Distribution checks on Avro eventAttribute?**
- RESOLVED: Yes -- must explode the stringified JSON keys inside `eventAttribute` map and treat them as virtual columns. Distribution checks (#7-10) run on both top-level fields AND exploded nested keys.

**Q34: Exchange #16 (Duplicate Primary Key)?**
- RESOLVED: DROPPED from scope. No primary key information available from any source.

**Q35: DQS Score weighting?**
- RESOLVED: Configurable weights. As checks expand across tiers, the scoring formula must accommodate new check categories with adjustable weights.

### Domain 8: Final Gaps

**Q36: Data retention / purging?**
- RESOLVED: Drop data after 90 days. Applies to metric tables and check results. Soft-delete via `expiry_date` pattern.

**Q37: Time zones?**
- RESOLVED: Eastern time for DQS operations. For freshness checks: `source_event_timestamp` can be assumed UTC always. The platform converts source timestamps to UTC before storage. Within a single source system, all `source_event_timestamp` values are from the same timezone. Current freshness check approach (parsing max timestamp from data) is valid -- just treat all values as UTC.

**Q38: Parquet schema variation?**
- RESOLVED: Runtime discovery from the Parquet file itself. Schema drift (#5) compares today's discovered schema against yesterday's stored schema hash. No predefined schema definitions needed.

**Q39: Summary email contents?**
- RESOLVED: Orchestration run ID, start/end time, total datasets scanned, pass/fail/error counts, top failures by check type, link to dashboard. Good starting point.

**Q40: Deployment model for serve-api?**
- RESOLVED: Docker-compose. No health checks, no readiness probes. Simple deployment.

---

## Technique 2: Six Thinking Hats

### White Hat (Facts)
- ~15-20% of v1 scope built. Spark check execution core is solid foundation (MetadataRepository, dual-format, 5 checks). Everything else (discovery, orchestration, data model, serve layer) is missing or needs rework.

### Red Hat (Emotions/Gut Feel)

**Resolved risks:**

**eventAttribute JSON explosion at scale:**
- RESOLVED: Three-position toggle: ALL (explode for every dataset) / CRITICAL (explode only for designated critical datasets) / OFF. Start with ALL enabled, tune down if performance bottleneck hit. Toggle is a system-level config, not per-dataset.

**Serve layer extensibility without over-engineering:**
- RESOLVED: Design API around the data model, not specific checks. Endpoints like `/api/datasets/{id}/metrics?type=numeric` and `?type=detail` naturally serve any check that writes to those tables. UI drills down by `check_type` as a filter param. No check-specific endpoints.

**Orchestration loop -- what is it?**
- RESOLVED: Python CLI tool (Option B). Handles: path scanning, spark-submit invocation per dataset, failure isolation, DB writes for orchestration run tracking, rerun numbering, and sends summary email at end. Not a shell script (too fragile), not Spark-in-Spark (too complex).

### Black Hat (Risks) -- Resolved

**R1: eventAttribute JSON explosion depth?**
- RESOLVED: Recursive flattening. Go as deep as the nesting goes. Produces virtual column names like `paymentDetails.items[0].amount`.

**R2: Schema drift noise from eventAttribute changes?**
- RESOLVED: YAML config with default "all checks apply to all datasets." If specific checks are too noisy for certain datasets, exclude them per-dataset in config. This gives fine-grained control without per-check-per-dataset complexity upfront.

**R3: Orchestrator failure mid-run recovery?**
- RESOLVED: Reprocess everything on rerun. Previous run's metric calculations get expired (via `expiry_date`). No skip-ahead logic needed. Simple and auditable.

**R4: DB write contention from concurrent Spark jobs?**
- RESOLVED: Spark job collects all results in memory and writes once after successful run. No concurrent JDBC calls per-check. Single batch write per dataset reduces connection pressure dramatically.

**R5: Summary email delayed by hung dataset?**
- RESOLVED: No per-dataset timeout in DQS. SLA breach alerting handled separately (outside DQS scope for now).

**R6: DB schema rework is breaking?**
- RESOLVED: Acceptable. Project is in dev stage. Treat as fresh start for data layer.

**R7: Scope is large for one developer?**
- RESOLVED: BMad-driven implementation. Deploy Tier 1 to prod, validate, then start next epic. Incremental de-risking.

**R-KEY: eventAttribute explosion performance concern:**
- RESOLVED: Granular dial-down approach rather than all-or-nothing toggle. Expand the three-position toggle to support step-by-step reduction:
  - ALL datasets: full recursive explosion
  - CRITICAL datasets only: explosion for designated datasets
  - TOP-LEVEL only: skip explosion, run distribution checks on top-level columns only
  - OFF: skip distribution checks entirely
- Start with ALL, dial down one step at a time if performance issues arise.

### Green Hat (Creativity) -- Innovations

**G1: Should orchestrator and serve-api share a codebase?**
- RESOLVED: No. Keep them separate. Functionally different things -- orchestrator is a CLI batch tool, serve-api is a long-running web service. Separate codebases, separate deployments.

**G2: Check exclusion config -- static YAML or DB?**
- RESOLVED: DB-driven. New `check_config` table with `(dataset_pattern, check_type, enabled, explosion_level)` + mandatory `create_date`/`expiry_date`. Enables future UI-based config by data stewards without redeployment.

**G3: Legacy lookup table → dataset enrichment table?**
- RESOLVED: Yes. Expand the legacy lookup table into a broader `dataset_enrichment` table. Beyond `src_sys_nm → lookup_code`, add columns for: custom DQS score weights, custom SLA hours, explosion_level per dataset, and any future per-dataset overrides. Single place to customize DQS behavior per dataset. Avoids scattering config across YAML + DB + code.

### Blue Hat (Process) -- Epic Structure

**Agreed epic structure:**

| Epic | Scope | Outcome |
|------|-------|---------|
| E1: Foundation Rework | DB schema redesign (all tables + new tables), test data fixtures, remove dead code | Clean foundation |
| E2: Spark Core + Orchestrator | Path scanner (extensible interface), dataset discovery, backfill, orchestrator CLI, batch DB writes, rerun tracking | E2E dataset processing |
| E3: Tier 1 Full Stack | 5 checks on new schema, configurable DQS Score, serve API drill-down, UI (summary/LOB/dataset views), MCP tools, summary email | **Deploy to prod** |
| E4: Tier 2 Checks | SLA Countdown, Zero-Row, Breaking Change, Distribution #7-10 (eventAttribute explosion), Timestamp Sanity | Deeper detection |
| E5: Tier 3 Intelligence | Classification-weighted alerting, source system health, correlation, inferred SLA, executive reporting suite | Strategic dashboards |
| E6: Tier 4 Advanced | Lineage checks, cross-destination consistency | Full vision |

**Execution approach:** BMad-driven. Deploy after E3, validate in prod, then proceed to E4+.

---

## Technique 3: Constraint Mapping

### Real Constraints (Non-negotiable)
- C1: ~400 GB/day on HDFS, Spark on YARN → Java Spark job required
- C2: Kerberos auth in production (keytab). Local dev bypasses with username/password
- C3: No schema definitions → all discovery at runtime, drift = compare to yesterday
- C4: No data domain knowledge → all checks statistical/structural only
- C5: Dataset registration service evolving → loosely coupled reference data scaffold
- C6: Bank context → audit trail mandatory, soft-delete everywhere, no hard deletes
- C7: Single developer using BMad → epic sizing must be achievable solo
- C8: Partitioning job finishes ~3am ET → DQS runs after

### Imagined Constraints (Released)
- I1: "8 CLI args" → orchestrator changes invocation model
- I2: "Need rules.yaml" → hardcoded checks + DB-driven config
- I3: "Metadata-level only" → DQS reads actual data, eventAttribute explosion
- I4: "Need all 30 checks for v1" → all in PRD, but prod deploy at E3 (Tier 1)
- I5: "Need auth" → no, internal network boundary
- I6: "CheckConfig/RuleConfig useful" → dead code, remove

### Scope Boundary

**IN SCOPE (v1 -- Epics E1-E6):**

Compute:
- Path scanner (extensible interface), recursive eventAttribute explosion (4-position dial: ALL/CRITICAL/TOP-LEVEL/OFF), backfill via date param
- Tier 1: Freshness #1, Volume #3, Schema #5, Ops #24, DQS Score #25
- Tier 2: SLA Countdown #2, Zero-Row #4, Breaking Change #6, Distribution #7-10, Timestamp Sanity #18
- Tier 3: Classification-Weighted #51, Source System Health #52, Correlation #55, Inferred SLA #57, Executive #26-30
- Tier 4: Lineage #11, Orphan Detection #12, Cross-Destination #53
- Batch DB write per dataset, configurable DQS Score weights

Orchestrator (Python CLI):
- Path scanning from configured parent paths, spark-submit per dataset with failure isolation
- Orchestration run tracking + rerun number, expire previous metrics on rerun
- Summary email to SRE, 90-day retention purge

Database:
- All tables: `create_date`/`expiry_date` soft-delete convention
- Redesigned: `dataset`, `dq_run`, `dq_check_result`
- New: `dq_orchestration_run`, `dq_metric_numeric`, `dq_metric_detail`, `check_config`, `dataset_enrichment`

Serve (Python/FastAPI):
- REST API designed around data model (generic, not check-specific)
- Drill-down: Summary → LOB → Dataset → Check Details
- Reference data resolution at query time, twice-daily cache refresh
- Lightweight UI framework, MCP tools via same API, no auth

Test Data:
- 3-5 mock source systems (Avro + Parquet), mixed anomalies, legacy path format
- Single date on disk, mocked history in DB
- eventAttribute with nested JSON (arrays, objects, mixed types)

**OUT OF SCOPE:**
- Airflow/Control-M integration (manual trigger only)
- "Who watches the watcher" heartbeat
- Authentication/authorization
- Checks: Duplicate Primary Key #16, Governance #20, Operations #22/#23
- Health checks, Kubernetes, multi-region/HA

**SCAFFOLD FOR LATER:**
- Reference data resolution from registration service DB (mock returning N/A)
- Alerting beyond summary email (interface defined, not implemented)
- Dataset registration API integration (extensible interface ready)
