# Requirements Inventory

## Functional Requirements

FR1: System can scan configured parent HDFS paths and discover dataset subdirectories automatically
FR2: System can extract dataset name (including lookup code) and partition date from HDFS path structures
FR3: System can support different HDFS path structures via an extensible interface
FR4: System can read Avro-format files from discovered dataset paths
FR5: System can read Parquet-format files from discovered dataset paths
FR6: System can resolve legacy dataset paths (e.g., `src_sys_nm=omni`) to lookup codes via the dataset_enrichment table
FR7: System can process a specific date via parameter for backfill runs
FR8: System can execute Freshness check (#1) — detect staleness by comparing latest source_event_timestamp against expected recency
FR9: System can execute Volume check (#3) — detect anomalous row count changes compared to historical baseline
FR10: System can execute Schema check (#5) — detect schema drift by comparing today's discovered schema against yesterday's stored schema hash
FR11: System can execute Ops check (#24) — detect operational anomalies (null rates, empty strings, data type consistency)
FR12: System can compute DQS Score (#25) — weighted composite score across all executed checks with configurable weights
FR13: System can execute SLA Countdown check (#2) — measure time remaining against expected delivery window (Phase 2)
FR14: System can execute Zero-Row check (#4) — detect datasets with no records (Phase 2)
FR15: System can execute Breaking Change check (#6) — detect schema changes that remove or rename fields (Phase 2)
FR16: System can execute Distribution checks (#7-10) — detect statistical distribution shifts across columns (Phase 2)
FR17: System can recursively explode eventAttribute JSON keys into virtual columns for distribution analysis (Phase 2)
FR18: System can control eventAttribute explosion depth via 4-position dial: ALL / CRITICAL / TOP-LEVEL / OFF (Phase 2)
FR19: System can execute Timestamp Sanity check (#18) — detect out-of-range or future-dated timestamps (Phase 2)
FR20: System can execute Classification-Weighted Alerting (#51), Source System Health (#52), Correlation (#55), Inferred SLA (#57) (Phase 3)
FR21: System can execute Lineage (#11), Orphan Detection (#12), Cross-Destination Consistency (#53) (Phase 3)
FR22: System can collect all check results in memory and write once per dataset as a batch DB operation
FR23: Platform ops can trigger an orchestration run manually via Python CLI
FR24: System can invoke spark-submit for each discovered dataset within a parent path
FR25: System can isolate failures so that one dataset failure does not halt the entire run
FR26: System can track orchestration runs with a dq_orchestration_run record grouping all per-dataset dq_run records
FR27: System can track rerun attempts with incrementing rerun_number per dataset
FR28: System can expire previous run's metric calculations (via expiry_date) when a rerun is triggered
FR29: System can capture error messages for failed datasets in the orchestration record
FR30: System can record dataset counts per run (total, passed, failed)
FR31: System can store all records with create_date and expiry_date columns (soft-delete temporal pattern)
FR32: System can store numeric metrics (row counts, percentages, scores, stddev) in dq_metric_numeric table
FR33: System can store structured/text results (schema diffs, duplicate values, change classifications) in dq_metric_detail table
FR34: System can manage per-dataset check configuration via check_config table (dataset_pattern, check_type, enabled, explosion_level)
FR35: System can manage per-dataset enrichment overrides via dataset_enrichment table (lookup code, custom weights, SLA hours, explosion level)
FR36: Data steward can view a summary of all monitored datasets with overall quality status
FR37: Data steward can drill down from summary to LOB-level aggregation
FR38: Data steward can drill down from LOB to individual dataset detail
FR39: Data steward can drill down from dataset to individual check results and metric values
FR40: Data steward can view trend visualization for DQS scores and check metrics over time
FR41: Downstream consumer can search for a specific dataset by name
FR42: Downstream consumer can see pass/fail status and key indicators (freshness, volume) at a glance
FR43: Executive can view monthly trending of quality scores by LOB
FR44: Executive can compare quality across LOBs
FR45: Data owner can filter datasets by source system to review their data's quality
FR46: System can resolve lookup codes to LOB, owner, and classification at query time in the serve layer
FR47: System can cache reference data with twice-daily refresh
FR48: System can return "N/A" for unresolved lookup codes (scaffold for future registration service integration)
FR49: External systems can access all dashboard capabilities via REST API with data-model-driven endpoints
FR50: LLM consumers can query data quality information via MCP tools using natural language
FR51: MCP tools can answer questions like "What failed last night?", "Show trending for dataset X", "Which LOB has worst quality?"
FR52: System can send summary email to SRE team after each orchestration run containing: run ID, start/end time, dataset counts, pass/fail/error counts, top failures by check type, dashboard link
FR53: Test data fixtures include 3-5 mock source systems covering Avro and Parquet formats with different path structures
FR54: Test data includes mixed anomalies within otherwise healthy datasets (stale partitions, zero rows, schema drift, high nulls)
FR55: Test data includes at least one legacy-format path (e.g., `src_sys_nm=omni`) to exercise lookup table resolution
FR56: Test data eventAttribute values cover all JSON literal types: strings, numbers, booleans, arrays, objects, and nested combinations
FR57: Historical baseline data for statistical checks is mocked directly in DB tables (single date on disk, mocked history in DB)
FR58: Executive can view executive reporting suite with strategic dashboards (#26-30) (Phase 3)

## NonFunctional Requirements

NFR1: Dashboard summary view loads within 2 seconds
NFR2: Drill-down navigation (LOB -> dataset -> check detail) responds within 1 second
NFR3: Standard API queries < 500ms
NFR4: Trend queries spanning 90 days < 2 seconds
NFR5: Dashboard supports dozens of simultaneous users (morning peak after overnight run)
NFR6: Full orchestration run across all configured parent paths completes within 2-3 hours after partitioning job finishes (~3am ET)
NFR7: Batch DB write — single write per dataset after all checks complete, no per-check DB round trips during Spark execution
NFR8: Reference data cache — twice-daily refresh, query-time join for LOB/owner resolution
NFR9: No application-level authentication — DQS accessible only within internal network/VPN
NFR10: Spark reads raw HDFS data containing PII/PCI but only computes and stores aggregate metrics — no raw data values persisted in Postgres or exposed via API/dashboard/MCP
NFR11: eventAttribute explosion reports key names only (e.g., `paymentDetails.items[0].amount`) — actual field values never stored or displayed
NFR12: Production HDFS access uses keytab-based Kerberos authentication; local dev bypasses with username/password
NFR13: System scales from initial tens of datasets (one parent path) to hundreds (all parent paths) without architectural changes
NFR14: Data volume designed for ~400 GB/day (stable), no requirement to scale beyond current volume
NFR15: Adding new check types (Tier 2-4) requires no changes to serve layer, API, or dashboard — generic metric consumption from dq_metric_numeric and dq_metric_detail
NFR16: 90-day data retention via soft-delete (expiry_date pattern), DB size grows linearly bounded by retention window
NFR17: HDFS read-only access via Spark on YARN, Avro and Parquet formats, Kerberos-authenticated in production
NFR18: Postgres primary data store — JDBC from Spark, SQLAlchemy from Python serve layer
NFR19: SMTP summary email delivery to SRE distribution list after each orchestration run
NFR20: Dataset registration service loosely coupled scaffold — interface defined, returns N/A until implemented, must not break if unavailable
NFR21: FastMCP tools expose API endpoints for LLM consumption — same data, different interface
NFR22: Single dataset failure must not halt orchestration run — all other datasets continue processing
NFR23: No data loss on failure — partial runs recorded with error details, reruns tracked with incrementing rerun_number, previous metrics expired cleanly
NFR24: Single-instance deployment via docker-compose — no HA, downtime during maintenance acceptable
NFR25: Dashboard best-effort availability during business hours — no uptime SLA, stale data from previous run acceptable if current run fails

## Additional Requirements

- **Starter template specified for each component:** dqs-dashboard (Vite 8 + React 19 + MUI 7 + TypeScript), dqs-serve (FastAPI 0.135 + SQLAlchemy 2.0 + FastMCP 3.2), dqs-orchestrator (Python CLI + psycopg2 + pyyaml), dqs-spark (Java/Maven + Spark 3.x)
- **Self-contained component architecture:** Each component has its own config/, dependencies, and build — designed to split into independent repos without changes
- **Postgres as integration layer:** All inter-component communication flows through Postgres only — no direct calls between components
- **Temporal pattern enforcement:** All tables use TIMESTAMP columns with sentinel `9999-12-31 23:59:59` for expiry_date, composite unique constraints on natural keys + expiry_date, active-record views (v_*_active) for query safety
- **Cross-runtime sentinel consistency:** Sentinel timestamp constant defined independently in each runtime (Java and Python), validated by shared test cases
- **Strategy pattern interface (DqCheck):** Extensible check architecture in Spark — all checks write to generic metric tables, serve/API/dashboard have zero check-type awareness
- **Data sensitivity boundary via DqMetric interface:** Checks output aggregate metrics only, detail metrics may contain field names but never source data values
- **Configuration landscape:** Parent paths in YAML config, check enablement and dataset enrichment in DB tables, runtime parameters via CLI arguments — no admin UI for MVP
- **Docker-compose deployment:** postgres (port 5432), serve (port 8000), dashboard (port 5173 dev / port 80 prod via nginx)
- **Unidirectional dependency chain:** Postgres schema -> Spark + Orchestrator -> Serve + API -> Dashboard + MCP (no circular dependencies)
- **Implementation sequence:** (1) Postgres schema DDL + views, (2) dqs-spark DqCheck + Tier 1 checks + BatchWriter, (3) dqs-orchestrator CLI + runner + email, (4) dqs-serve API + reference data + MCP, (5) dqs-dashboard pages + components + React Query
- **Naming conventions per runtime:** Java (PascalCase classes, camelCase methods), Python (snake_case everything, PascalCase classes), TypeScript/React (PascalCase components, camelCase hooks/utilities)
- **API snake_case JSON throughout:** Matches Postgres columns and Python conventions, dashboard consumes as-is
- **Error handling per component:** Spark catches per-dataset exceptions and continues; orchestrator catches per-spark-submit failures via exit code; serve returns consistent `{"detail": "...", "error_code": "..."}` JSON; dashboard uses component-level error display
- **Logging:** SLF4J/Log4j for Spark, structured console logging for Python components, run_id correlation across all components
- **Testing:** JUnit (Spark), pytest (orchestrator + serve against real Postgres), Vitest (dashboard) — test fixtures in each component's tests/fixtures/
- **Brownfield context:** Existing prototype (~15-20%) provides domain knowledge and reference. All components designed fresh — DB schema, Spark checks, serve layer, frontend. ~5-10% reusable code, ~80% reusable domain knowledge

## UX Design Requirements

UX-DR1: Implement MUI 7 custom theme with muted neutral palette (neutral-50 through neutral-900), semantic DQS Score colors (success green #2E7D32 >= 80, warning amber #ED6C02 60-79, error red #D32F2F < 60), accent primary #1565C0, and light tint variants for card backgrounds
UX-DR2: Implement typography system with system font stack, defined type scale (h1 24px through caption 12px), score display sizes (28px large, 18px card), and monospace font for dataset names, lookup codes, and technical identifiers
UX-DR3: Implement 8px grid spacing system with defined tokens (spacing-xs 4px through spacing-2xl 48px), max content width 1440px centered, 12-column MUI Grid layout
UX-DR4: Build DqsScoreChip custom component — color-coded score badge with trend arrow and delta, 3 sizes (lg/md/sm), threshold-based coloring, "no data" and loading states, aria-label with score + status + trend
UX-DR5: Build DatasetCard (LOB Card) custom component — LOB name, dataset count, DqsScoreChip, score progress bar, TrendSparkline, status count chips (pass/warn/fail), hover border transition, full-card clickable with role="button" and keyboard support
UX-DR6: Build TrendSparkline custom component — Recharts inline sparkline with 3 sizes (lg 64px / md 32px / mini 24px+80px), threshold-based line color, optional baseline at score 80, tooltip on hover (except mini), aria-label summarizing trend
UX-DR7: Build DatasetInfoPanel custom component — structured metadata display (source system, LOB, owner, format, HDFS path, parent path, partition date, row count with delta, last updated, run ID, rerun number), HDFS path copy-to-clipboard button, error message alert box, semantic dl/dt/dd structure for accessibility
UX-DR8: Implement Summary Card Grid view (Level 1) — stats bar (total datasets, healthy, degraded, critical counts) + 3-column LOB card grid using DatasetCard components, landing page for assess-and-drill flow
UX-DR9: Implement LOB Detail view (Level 2) — stats header (LOB name, dataset count, avg DQS score + trend, checks passing rate, last run time) + MUI DataGrid sortable table with columns: dataset name (mono), DQS Score (color-coded), trend sparkline (mini), freshness/volume/schema status chips, overall status chip — default sort by DQS Score ascending (worst first)
UX-DR10: Implement Dataset Detail view (Level 3) — Master-Detail split with left panel (scrollable dataset list for current LOB with DqsScoreChip + name + check summary + trend, active item highlighted) + right panel (dataset header with name + status chip + metadata line, 2-column grid with DQS Score + trend chart + check results list on left, score breakdown card + DatasetInfoPanel on right)
UX-DR11: Implement fixed header with breadcrumb navigation (Summary > LOB > Dataset), time range toggle (7d/30d/90d) using MUI ToggleButtonGroup, and global search bar — persistent across all views, sticky positioning
UX-DR12: Implement dataset search autocomplete using MUI Autocomplete — Ctrl+K keyboard shortcut, 300ms debounce, results show DqsScoreChip + dataset name (mono) + LOB name (gray), global scope across all LOBs, arrow-key navigation, max 10 results
UX-DR13: Implement deep-linkable routes via React Router 7 — /summary, /lob/:id, /dataset/:id — browser back/forward works, URLs bookmarkable and shareable, summary email can link directly to specific views
UX-DR14: Implement React Query (TanStack Query) for data fetching — caching between drill-down levels, skeleton loading states, stale-while-revalidate pattern, global time range change invalidates all queries
UX-DR15: Implement skeleton loading states (no spinners) matching target layout dimensions — cards show skeleton rectangles, table shows skeleton rows, layout must not shift when data arrives, instant breadcrumb update on navigation, sparklines fade to 50% opacity during time range change
UX-DR16: Implement empty & no-data states with explanatory messages — "No datasets monitored in {LOB}", "No check results available", single-dot sparkline for new datasets with "First run" label, "No datasets matching '{query}'" in search, full-page empty state when system has no data, "N/A" for unresolved reference data
UX-DR17: Implement error & stale data states — yellow banner for failed runs showing previous results, red "ERROR" chip for dataset Spark failures with error message in DatasetInfoPanel, amber "Last updated" when run >24h old, full-page error for API unreachable with retry button, component-level error isolation ("Failed to load" with retry link)
UX-DR18: Implement data freshness indicators — "Last updated: {time} ET" in fixed header (gray when fresh, amber when stale >12h), per-dataset timestamp in DatasetInfoPanel, rerun number badge, stale data banner when latest run >24h old
UX-DR19: Implement WCAG 2.1 AA accessibility — skip-to-content link, landmark regions (header/main/nav), aria-live="polite" for data updates on time range change, DataGrid with proper aria-colcount/aria-rowcount, minimum 44x44px touch targets, MUI default focus rings preserved
UX-DR20: Implement axe-core (@axe-core/react) for automated accessibility scanning during development
UX-DR21: Implement desktop viewport handling — standard layout at 1280-1440px, centered 1440px max-width at >1440px, graceful degradation at <1280px (2-column cards, horizontal table scroll, narrower left panel)
UX-DR22: Implement card visual design — 1px neutral-200 border, border-radius 8px, no box-shadow, hover border transitions to primary color, consistent card heights per drill-down level, content truncation with tooltip on hover
UX-DR23: Implement navigation patterns — breadcrumbs as sole navigation mechanism (no sidebar/hamburger/tabs), browser back/forward via React Router history, URL reflects current view state, left panel click updates right panel content with URL update (no full page navigation)

## FR Coverage Map

FR1: Epic 2 — HDFS path scanning and dataset discovery
FR2: Epic 2 — Dataset name and partition date extraction from paths
FR3: Epic 2 — Extensible path scanner interface
FR4: Epic 2 — Avro format reading
FR5: Epic 2 — Parquet format reading
FR6: Epic 2 — Legacy path resolution via dataset_enrichment table
FR7: Epic 2 — Backfill via date parameter
FR8: Epic 2 — Freshness check (#1)
FR9: Epic 2 — Volume check (#3)
FR10: Epic 2 — Schema check (#5)
FR11: Epic 2 — Ops check (#24)
FR12: Epic 2 — DQS Score computation (#25)
FR13: Epic 6 — SLA Countdown check (#2) [Phase 2]
FR14: Epic 6 — Zero-Row check (#4) [Phase 2]
FR15: Epic 6 — Breaking Change check (#6) [Phase 2]
FR16: Epic 6 — Distribution checks (#7-10) [Phase 2]
FR17: Epic 6 — eventAttribute JSON explosion [Phase 2]
FR18: Epic 6 — eventAttribute explosion depth dial [Phase 2]
FR19: Epic 6 — Timestamp Sanity check (#18) [Phase 2]
FR20: Epic 7 — Classification-Weighted Alerting, Source System Health, Correlation, Inferred SLA [Phase 3]
FR21: Epic 7 — Lineage, Orphan Detection, Cross-Destination Consistency [Phase 3]
FR22: Epic 2 — Batch DB write (collect in memory, write once per dataset)
FR23: Epic 3 — Python CLI trigger for orchestration run
FR24: Epic 3 — spark-submit invocation per parent path
FR25: Epic 3 — Failure isolation (one dataset failure doesn't halt run)
FR26: Epic 3 — Orchestration run tracking (dq_orchestration_run)
FR27: Epic 3 — Rerun tracking with incrementing rerun_number
FR28: Epic 3 — Previous metric expiration on rerun
FR29: Epic 3 — Error message capture for failed datasets
FR30: Epic 3 — Dataset counts per run (total, passed, failed)
FR31: Epic 1 — Temporal pattern (create_date/expiry_date on all tables)
FR32: Epic 1 — dq_metric_numeric table for numeric metrics
FR33: Epic 1 — dq_metric_detail table for structured/text results
FR34: Epic 1 — check_config table for per-dataset check configuration
FR35: Epic 1 — dataset_enrichment table for per-dataset overrides
FR36: Epic 4 — Summary view of all monitored datasets
FR37: Epic 4 — Summary to LOB drill-down
FR38: Epic 4 — LOB to dataset drill-down
FR39: Epic 4 — Dataset to check results drill-down
FR40: Epic 4 — Trend visualization for DQS scores
FR41: Epic 4 — Dataset search by name
FR42: Epic 4 — Pass/fail status at a glance
FR43: Epic 4 — Monthly trending by LOB
FR44: Epic 4 — Cross-LOB comparison
FR45: Epic 4 — Source system filtering
FR46: Epic 4 — Lookup code to LOB/owner resolution
FR47: Epic 4 — Reference data cache with twice-daily refresh
FR48: Epic 4 — N/A fallback for unresolved lookup codes
FR49: Epic 4 — REST API with data-model-driven endpoints
FR50: Epic 5 — MCP tools for LLM natural language queries
FR51: Epic 5 — MCP query support (failures, trending, worst quality)
FR52: Epic 3 — Summary email to SRE team
FR53: Epic 1 — Test data fixtures (3-5 mock source systems)
FR54: Epic 1 — Test data with mixed anomalies
FR55: Epic 1 — Test data with legacy-format path
FR56: Epic 1 — Test data eventAttribute JSON types
FR57: Epic 1 — Historical baseline data mocked in DB
FR58: Epic 7 — Executive reporting suite [Phase 3]
