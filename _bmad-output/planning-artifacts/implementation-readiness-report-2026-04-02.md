---
stepsCompleted:
  - step-01-document-discovery
  - step-02-prd-analysis
  - step-03-epic-coverage-validation
  - step-04-ux-alignment
  - step-05-epic-quality-review
  - step-06-final-assessment
filesIncluded:
  - prd.md
filesMissing:
  - architecture
  - epics-and-stories
  - ux-design
---

# Implementation Readiness Assessment Report

**Date:** 2026-04-02
**Project:** data-quality-service

## 1. Document Inventory

### PRD
- **Status:** Found
- **File:** `_bmad-output/planning-artifacts/prd.md`
- **Format:** Whole document

### Architecture
- **Status:** NOT FOUND
- **Impact:** Cannot validate technical design alignment

### Epics & Stories
- **Status:** NOT FOUND
- **Impact:** Cannot validate implementation breakdown or requirements traceability

### UX Design
- **Status:** NOT FOUND
- **Impact:** Cannot validate user experience alignment with PRD

## 2. PRD Analysis

### Functional Requirements

**Data Discovery & Ingestion (FR1-FR7)**
- FR1: System can scan configured parent HDFS paths and discover dataset subdirectories automatically
- FR2: System can extract dataset name (including lookup code) and partition date from HDFS path structures
- FR3: System can support different HDFS path structures via an extensible interface
- FR4: System can read Avro-format files from discovered dataset paths
- FR5: System can read Parquet-format files from discovered dataset paths
- FR6: System can resolve legacy dataset paths (e.g., `src_sys_nm=omni`) to lookup codes via the dataset_enrichment table
- FR7: System can process a specific date via parameter for backfill runs

**Quality Check Execution (FR8-FR22)**
- FR8: System can execute Freshness check (#1) — detect staleness by comparing latest source_event_timestamp against expected recency
- FR9: System can execute Volume check (#3) — detect anomalous row count changes compared to historical baseline
- FR10: System can execute Schema check (#5) — detect schema drift by comparing today's discovered schema against yesterday's stored schema hash
- FR11: System can execute Ops check (#24) — detect operational anomalies (null rates, empty strings, data type consistency)
- FR12: System can compute DQS Score (#25) — weighted composite score across all executed checks with configurable weights
- FR13: System can execute SLA Countdown check (#2) — measure time remaining against expected delivery window (Phase 2)
- FR14: System can execute Zero-Row check (#4) — detect datasets with no records (Phase 2)
- FR15: System can execute Breaking Change check (#6) — detect schema changes that remove or rename fields (Phase 2)
- FR16: System can execute Distribution checks (#7-10) — detect statistical distribution shifts across columns (Phase 2)
- FR17: System can recursively explode eventAttribute JSON keys into virtual columns for distribution analysis (Phase 2)
- FR18: System can control eventAttribute explosion depth via 4-position dial: ALL / CRITICAL / TOP-LEVEL / OFF (Phase 2)
- FR19: System can execute Timestamp Sanity check (#18) — detect out-of-range or future-dated timestamps (Phase 2)
- FR20: System can execute Classification-Weighted Alerting (#51), Source System Health (#52), Correlation (#55), Inferred SLA (#57) (Phase 3)
- FR21: System can execute Lineage (#11), Orphan Detection (#12), Cross-Destination Consistency (#53) (Phase 3)
- FR22: System can collect all check results in memory and write once per dataset as a batch DB operation

**Orchestration & Run Management (FR23-FR30)**
- FR23: Platform ops can trigger an orchestration run manually via Python CLI
- FR24: System can invoke spark-submit for each discovered dataset within a parent path
- FR25: System can isolate failures so that one dataset failure does not halt the entire run
- FR26: System can track orchestration runs with a dq_orchestration_run record grouping all per-dataset dq_run records
- FR27: System can track rerun attempts with incrementing rerun_number per dataset
- FR28: System can expire previous run's metric calculations (via expiry_date) when a rerun is triggered
- FR29: System can capture error messages for failed datasets in the orchestration record
- FR30: System can record dataset counts per run (total, passed, failed)

**Data Model & Audit (FR31-FR35)**
- FR31: System can store all records with create_date and expiry_date columns (soft-delete temporal pattern)
- FR32: System can store numeric metrics (row counts, percentages, scores, stddev) in dq_metric_numeric table
- FR33: System can store structured/text results (schema diffs, duplicate values, change classifications) in dq_metric_detail table
- FR34: System can manage per-dataset check configuration via check_config table (dataset_pattern, check_type, enabled, explosion_level)
- FR35: System can manage per-dataset enrichment overrides via dataset_enrichment table (lookup code, custom weights, SLA hours, explosion level)

**Reporting Dashboard (FR36-FR45)**
- FR36: Data steward can view a summary of all monitored datasets with overall quality status
- FR37: Data steward can drill down from summary to LOB-level aggregation
- FR38: Data steward can drill down from LOB to individual dataset detail
- FR39: Data steward can drill down from dataset to individual check results and metric values
- FR40: Data steward can view trend visualization for DQS scores and check metrics over time
- FR41: Downstream consumer can search for a specific dataset by name
- FR42: Downstream consumer can see pass/fail status and key indicators (freshness, volume) at a glance
- FR43: Executive can view monthly trending of quality scores by LOB
- FR44: Executive can compare quality across LOBs
- FR45: Data owner can filter datasets by source system to review their data's quality

**Reference Data Resolution (FR46-FR48)**
- FR46: System can resolve lookup codes to LOB, owner, and classification at query time in the serve layer
- FR47: System can cache reference data with twice-daily refresh
- FR48: System can return "N/A" for unresolved lookup codes (scaffold for future registration service integration)

**API & Integration (FR49-FR52)**
- FR49: External systems can access all dashboard capabilities via REST API with data-model-driven endpoints
- FR50: LLM consumers can query data quality information via MCP tools using natural language
- FR51: MCP tools can answer questions like "What failed last night?", "Show trending for dataset X", "Which LOB has worst quality?"
- FR52: System can send summary email to SRE team after each orchestration run containing: run ID, start/end time, dataset counts, pass/fail/error counts, top failures by check type, dashboard link

**Test Data (FR53-FR57)**
- FR53: Test data fixtures include 3-5 mock source systems covering Avro and Parquet formats with different path structures
- FR54: Test data includes mixed anomalies within otherwise healthy datasets (stale partitions, zero rows, schema drift, high nulls)
- FR55: Test data includes at least one legacy-format path (e.g., `src_sys_nm=omni`) to exercise lookup table resolution
- FR56: Test data eventAttribute values cover all JSON literal types: strings, numbers, booleans, arrays, objects, and nested combinations
- FR57: Historical baseline data for statistical checks is mocked directly in DB tables (single date on disk, mocked history in DB)

**Executive Reporting — Phase 3 (FR58)**
- FR58: Executive can view executive reporting suite with strategic dashboards (#26-30) (Phase 3)

**Total FRs: 58**

### Non-Functional Requirements

**Performance**
- NFR1: Dashboard summary view loads within 2 seconds
- NFR2: Drill-down navigation (LOB → dataset → check detail) responds within 1 second
- NFR3: Standard API queries < 500ms
- NFR4: Trend queries spanning 90 days < 2 seconds
- NFR5: Dashboard supports dozens of simultaneous users (morning peak after overnight run completes)
- NFR6: Full orchestration run across all configured parent paths completes within 2-3 hours after partitioning job finishes (~3am ET)
- NFR7: Batch DB write — single write per dataset after all checks complete. No per-check DB round trips during Spark execution
- NFR8: Reference data cache with twice-daily refresh, query-time join for LOB/owner resolution

**Security**
- NFR9: No application-level authentication — DQS accessible only within internal network/VPN
- NFR10: Only aggregate metrics stored in Postgres — no raw PII/PCI data persisted or exposed via API/dashboard/MCP
- NFR11: eventAttribute explosion reports key names only — actual field values never stored or displayed
- NFR12: Production HDFS access uses keytab-based Kerberos authentication; local dev bypasses with username/password

**Scalability**
- NFR13: System scales from tens of datasets (one parent path) to hundreds (all parent paths) without architectural changes
- NFR14: Designed for ~400 GB/day (stable). No requirement to scale beyond current volume
- NFR15: Adding new check types (Tier 2-4) requires no changes to serve layer, API, or dashboard — generic metric consumption
- NFR16: 90-day data retention via soft-delete. DB size grows linearly with dataset count, bounded by retention window

**Integration**
- NFR17: HDFS read-only access via Spark on YARN. Avro and Parquet formats. Kerberos in production
- NFR18: Postgres primary data store — JDBC from Spark, SQLAlchemy from Python serve layer
- NFR19: SMTP summary email delivery to SRE distribution list
- NFR20: Dataset registration service loosely coupled scaffold — interface defined, returns N/A until implemented. Must not break if unavailable
- NFR21: FastMCP tools expose API endpoints for LLM consumption

**Reliability**
- NFR22: Single dataset failure must not halt orchestration run
- NFR23: No data loss on failure — partial runs recorded with error details, reruns tracked, previous metrics expired cleanly
- NFR24: No HA requirement — single-instance deployment via docker-compose. Downtime during maintenance acceptable
- NFR25: Dashboard best-effort availability during business hours. No uptime SLA. Stale data acceptable if current run fails

**Total NFRs: 25**

### Additional Requirements & Constraints

**Compliance & Regulatory**
- No direct regulatory mandate — internal observability tool
- Audit trail by convention — bank enforces soft-delete temporal patterns across all internal systems (create_date/expiry_date, no hard deletes)

**Data Sensitivity & Privacy**
- Spark reads sensitive data but DQS only stores/surfaces aggregate metrics
- eventAttribute explosion extracts key names only, never field values
- No data export — all outputs are statistical summaries

**Operational Constraints**
- Kerberos authentication required for HDFS/Spark in production (keytab-based)
- No application-level authentication (internal network boundary security)
- 90-day data retention via expiry_date pattern (purge automated outside DQS scope)
- Eastern time zone for all DQS operations; source timestamps treated as UTC
- Lightweight change management — no CAB approval required

**Browser & Platform**
- Modern browsers only: Chrome, Edge (latest versions)
- Desktop-first design — no tablet/mobile support required
- Internal network access only

**Technology Stack**
- Frontend: React SPA (client-side rendering, lightweight state management)
- Backend: FastAPI (Python), data-model-driven REST API
- MCP: FastMCP tools wrapping same API endpoints
- Deployment: Docker-compose

**Phasing**
- MVP (Phase 1): Epics E1-E3 — Foundation Rework, Spark Core + Orchestrator, Tier 1 Full Stack
- Growth (Phase 2): Epics E4-E5 — Tier 2 Checks, Tier 3 Intelligence
- Vision (Phase 3): Epic E6+ — Tier 4 Advanced, Airflow integration, Advanced alerting

### PRD Completeness Assessment

The PRD is **comprehensive and well-structured**. It contains:
- Clear executive summary and project classification
- 6 detailed user journeys with narrative arc
- 58 functional requirements covering all system components
- 25 non-functional requirements across performance, security, scalability, integration, and reliability
- Explicit phasing strategy (MVP → Growth → Vision)
- Risk mitigation for technical, adoption, and resource risks
- Domain-specific compliance, privacy, and operational constraints

**Noted gaps:** The PRD references React as the frontend framework, but the existing codebase appears to use vanilla JS. This may need reconciliation in architecture/implementation.

## 3. Epic Coverage Validation

### BLOCKER: Epics & Stories Document Not Found

**No epics and stories document exists in the planning artifacts.** Epic coverage validation cannot be performed.

### Coverage Statistics

- Total PRD FRs: 58
- FRs covered in epics: 0
- Coverage percentage: **0%**

### Impact Assessment

Without an epics document, there is:
- No traceable implementation path for any of the 58 functional requirements
- No story breakdown for developer execution
- No sprint planning basis
- No acceptance criteria defined

### Recommendation

**Create the epics and stories document before proceeding with implementation.** Use `/bmad-create-epics-and-stories` to break down the 58 FRs into implementable epics and stories. The PRD already outlines an epic structure (E1-E6) that should serve as a strong starting point.

## 4. UX Alignment Assessment

### UX Document Status

**Not Found.** No UX design document exists in planning artifacts.

### Is UX Implied?

**Yes — strongly implied.** The PRD describes a comprehensive reporting dashboard as a core system component:

- Summary view with overall quality status (FR36)
- LOB-level drill-down aggregation (FR37)
- Dataset-level detail view (FR38)
- Check results and metric values view (FR39)
- Trend visualization for scores and metrics over time (FR40)
- Dataset search by name (FR41)
- Pass/fail status with freshness/volume indicators at a glance (FR42)
- Executive monthly trending by LOB (FR43)
- Cross-LOB comparison view (FR44)
- Source system filtering (FR45)

Six user journeys describe specific UI interactions (steward drill-down, executive dashboards, dataset search, etc.).

### Alignment Issues

- **No UX specifications exist** to validate against PRD or Architecture
- PRD specifies React SPA but existing codebase uses vanilla JS — no UX doc to clarify the intended approach
- Dashboard view hierarchy (summary → LOB → dataset → check) is described in PRD but has no wireframes, component specs, or interaction patterns documented

### Warnings

- **WARNING: UX documentation is missing for a user-facing web application with 10+ dashboard-related FRs.** Without UX specs, developers will need to interpret UI requirements directly from PRD user journeys, which increases risk of rework and inconsistent implementation.
- **WARNING: No accessibility, responsive design, or interaction pattern guidance.** PRD states "desktop-first" but no further UX constraints are specified.

### Recommendation

Consider creating a UX design document (`/bmad-create-ux-design`) before implementing dashboard stories. At minimum, define:
1. View hierarchy and navigation patterns
2. Key dashboard layouts (summary, LOB, dataset detail)
3. Data visualization approach for trends
4. Search and filtering interactions

## 5. Epic Quality Review

### BLOCKER: Epics & Stories Document Not Found

**No epics and stories document exists.** Epic quality review cannot be performed.

Without an epics document, the following cannot be validated:
- Epic user-value focus (vs. technical milestones)
- Epic independence and ordering
- Story sizing and completeness
- Acceptance criteria quality (BDD format, testability)
- Dependency analysis (within-epic and cross-epic)
- Database/entity creation timing
- Brownfield integration points

### PRD-Based Preliminary Observations

The PRD outlines 6 epics (E1-E6) at a high level. Based on the PRD descriptions alone, some concerns are already visible:

#### Potential Issues to Watch When Creating Epics

1. **E1 "Foundation Rework" is a technical epic** — DB schema redesign, new tables, test data fixtures, dead code removal. This does not deliver user value directly. It should be restructured to include user-facing outcomes or folded into the first user-value epic.

2. **E2 "Spark Core + Orchestrator" is also technical** — path scanner, dataset discovery, Python CLI orchestrator. Again, no direct user value unless paired with observable outcomes.

3. **E3 "Tier 1 Full Stack" is the first user-value epic** — it includes the dashboard, API, MCP, and summary email. This means users get no value until E3 is complete, which undermines incremental delivery.

4. **Brownfield consideration:** PRD states ~15-20% is already implemented (5 Tier 1 Spark checks, DB schema, serve API skeleton). Epics must account for existing code — migration/refactoring stories rather than greenfield creation.

### Recommendation

When creating epics, restructure E1-E3 so that each epic delivers observable user value. Consider vertical slices (e.g., "End-to-end for one check type") rather than horizontal layers (e.g., "all DB work first, then all Spark work, then all API work").

## 6. Summary and Recommendations

### Overall Readiness Status

**NOT READY**

The project has a strong, comprehensive PRD but is missing 3 of 4 required planning artifacts. Implementation cannot proceed responsibly without at minimum an Architecture document and an Epics & Stories document.

### Critical Issues Requiring Immediate Action

| # | Issue | Severity | Impact |
|---|-------|----------|--------|
| 1 | **No Architecture document** | CRITICAL | No technical design decisions documented. Technology choices, component boundaries, data flow, and integration patterns are undefined. Developers will make ad-hoc architectural decisions that may conflict. |
| 2 | **No Epics & Stories document** | CRITICAL | 58 FRs have no implementation breakdown. No stories, no acceptance criteria, no sprint planning basis. 0% FR coverage. |
| 3 | **No UX Design document** | HIGH | 10+ dashboard FRs with no wireframes, layouts, or interaction patterns. High rework risk for the reporting dashboard. |
| 4 | **PRD says React, codebase uses vanilla JS** | MEDIUM | Technology mismatch needs resolution in Architecture document. |
| 5 | **PRD epic structure uses horizontal layers (E1=DB, E2=Spark, E3=UI)** | MEDIUM | First 2 epics deliver no user value. Should be restructured into vertical slices when creating epics document. |

### Recommended Next Steps

1. **Create Architecture document** (`/bmad-create-architecture`) — Define technology decisions, component boundaries, data flow, DB schema design, API contract patterns, and deployment architecture. Resolve the React vs. vanilla JS question. Account for the ~15-20% brownfield implementation.

2. **Create Epics & Stories document** (`/bmad-create-epics-and-stories`) — Break down all 58 FRs into implementable epics and stories with acceptance criteria. Restructure the PRD's E1-E3 horizontal layering into vertical slices that deliver incremental user value. Ensure every MVP FR (Phase 1) has a traceable story.

3. **Consider UX Design document** (`/bmad-create-ux-design`) — While optional for an internal tool, the dashboard is a core deliverable with 10 FRs. At minimum, define view hierarchy, key layouts, and data visualization approach before implementing dashboard stories.

4. **Re-run this readiness check** (`/bmad-check-implementation-readiness`) after creating the missing artifacts to validate alignment and completeness.

### What's Working Well

- The PRD is **exceptionally thorough** — 58 FRs, 25 NFRs, 6 user journeys, clear phasing, explicit risk mitigation
- Domain constraints (compliance, privacy, operational) are well-documented
- MVP scope is clearly defined with a sensible "deploy after E3" strategy
- Success criteria are measurable with baselines and targets

### Final Note

This assessment identified **5 issues across 3 categories** (missing documents, technology mismatch, epic structure concerns). The PRD provides a strong foundation, but 3 critical planning artifacts must be created before implementation can begin. The recommended sequence is: Architecture → Epics & Stories → (optional) UX Design → Re-run readiness check.
