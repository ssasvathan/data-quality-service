---
stepsCompleted:
  - step-01-init
  - step-02-discovery
  - step-02b-vision
  - step-02c-executive-summary
  - step-03-success
  - step-04-journeys
  - step-05-domain
  - step-06-innovation
  - step-07-project-type
  - step-08-scoping
  - step-09-functional
  - step-10-nonfunctional
  - step-11-polish
  - step-12-complete
inputDocuments:
  - _bmad-output/brainstorming/brainstorming-session-2026-04-02-2130.md
documentCounts:
  briefs: 0
  research: 0
  brainstorming: 1
  projectDocs: 0
classification:
  projectType: web_app
  domain: fintech
  complexity: high
  projectContext: brownfield
workflowType: 'prd'
---

# Product Requirements Document - data-quality-service

**Author:** Sas
**Date:** 2026-04-02

## Executive Summary

The Data Quality Service (DQS) is a bank-wide data quality platform that transforms data quality management from a reactive, complaint-driven process into a proactive, automated detection system. Processing ~400 GB/day across hundreds of datasets on HDFS, DQS runs statistical and structural quality checks via Apache Spark, stores results in Postgres, and surfaces insights through a drill-down reporting dashboard, REST API, and MCP-powered LLM interface.

The platform serves data stewards and executives who currently have no visibility into data quality until downstream consumers report failures. DQS provides trend analysis and anomaly detection across all datasets — enabling teams to identify degradation patterns and address gaps before they impact consumers.

### What Makes This Special

DQS operates without any data domain knowledge. The platform team does not understand the business meaning of the data it monitors — and it doesn't need to. All checks are purely statistical and structural: volume shifts, schema drift, freshness gaps, distribution anomalies, and null rate changes. This domain-agnostic approach means DQS scales across any dataset, any source system, and any format (Avro or Parquet) without per-dataset configuration or business rule authoring.

The core insight: at the scale of hundreds of datasets and thousands of consumers, human-driven quality monitoring is unsustainable. Automated, metadata-level intelligence that detects trends and surfaces anomalies is the only viable path — and it must work without requiring domain expertise the platform team doesn't have.

## Project Classification

- **Project Type:** Web application (multi-component: Spark job, Python orchestrator, FastAPI serve layer with reporting dashboard, MCP interface)
- **Domain:** Fintech (banking data quality platform)
- **Complexity:** High (regulated industry with audit-trail requirements, large-scale distributed data processing, multi-runtime technology stack, 4-tier check architecture)
- **Project Context:** Brownfield (~15-20% implemented — 5 Tier 1 Spark checks working, DB schema exists, serve API skeleton in place)

## Success Criteria

### User Success

- **Data stewards** can open the dashboard and immediately see what changed — new failures, trend shifts, schema drifts — without digging through logs or waiting for consumer complaints. The "Monday morning confidence" test: a steward can assess weekend data health in under 5 minutes.
- **Executives** review a monthly summary view showing quality trends by LOB, identify persistent problem areas, and track improvement over time. The dashboard supports drill-down from LOB summary to dataset-level detail when needed.

### Business Success

- **Complaint reduction:** Baseline of ~few complaints per week from downstream consumers. Target: measurable reduction within 3 months of prod deploy as DQS catches issues before consumers do.
- **Coverage rollout:** Start with one parent path (~tens of datasets), validate in production, then progressively onboard additional parent paths until full coverage across hundreds of datasets.
- **Proactive detection rate:** DQS surfaces quality issues before downstream consumer complaints. Success = the team learns about problems from DQS first, not from Slack messages.

### Technical Success

- **Processing window:** Full DQS run (orchestration + all checks for configured parent paths) completes within 2-3 hours after the partitioning job finishes (~3am ET). Checks available in dashboard before business hours.
- **Failure isolation:** A single dataset failure does not halt the entire run. Orchestrator processes all configured datasets and reports failures in the summary email.
- **Audit completeness:** Every run, rerun, and metric calculation is tracked with soft-delete temporal pattern. Full audit trail with no hard deletes.

### Measurable Outcomes

| Metric | Baseline | 3-Month Target | 12-Month Target |
|--------|----------|----------------|-----------------|
| Data quality complaints | Few per week | Measurable reduction | Rare (most caught by DQS first) |
| Dataset coverage | 0 | Tens (1 parent path) | Hundreds (all parent paths) |
| Processing time | N/A | < 3 hours | < 3 hours at full scale |
| Dashboard adoption | N/A | Data stewards using weekly | Execs reviewing monthly |

## Product Scope

### MVP Strategy & Philosophy

**MVP Approach:** Platform MVP — deploy a complete vertical slice (Spark checks → DB → API → Dashboard → MCP) for Tier 1 checks on a single parent path. Validate the full stack works end-to-end before expanding coverage or adding check complexity.

**Resource Requirements:** Single developer, BMad-driven implementation. Epics sized for solo execution with incremental validation.

### MVP Feature Set (Phase 1: Epics E1-E3)

- **E1 - Foundation Rework:** DB schema redesign (all tables with create_date/expiry_date convention), new tables (dq_orchestration_run, dq_metric_numeric, dq_metric_detail, check_config, dataset_enrichment), test data fixtures, dead code removal
- **E2 - Spark Core + Orchestrator:** Extensible path scanner, dataset discovery, backfill support, Python CLI orchestrator with failure isolation, batch DB writes, rerun tracking
- **E3 - Tier 1 Full Stack:** 5 checks (Freshness, Volume, Schema, Ops, DQS Score) on new schema, configurable score weights, serve API with drill-down endpoints, reporting dashboard (summary/LOB/dataset views), MCP tools, summary email
- **Deploy to production after E3.** Validate with one parent path (~tens of datasets) before expanding.

**Core User Journeys Supported:**
- **Data Steward (Priya):** Summary → LOB → dataset drill-down with Tier 1 check results and trends
- **Platform Ops (Sas):** Summary email, orchestration tracking, rerun management, config via DB tables
- **Downstream Consumer (Alex):** Dataset search by name, pass/fail status, freshness/volume indicators
- **LLM/MCP Consumer:** Natural language queries against the same API ("What failed last night?", "Show trending for dataset X")

### Growth Features (Phase 2: Epics E4-E5)

- **E4 - Tier 2 Checks:** SLA Countdown (#2), Zero-Row (#4), Breaking Change (#6), Distribution #7-10 (eventAttribute explosion with 4-position dial), Timestamp Sanity (#18)
- **E5 - Tier 3 Intelligence:** Classification-weighted alerting, source system health, correlation, inferred SLA, executive reporting suite
- Dataset enrichment integration with registration service (replace N/A scaffold)

### Vision (Phase 3: Epic E6+)

- **E6 - Tier 4 Advanced:** Lineage checks, cross-destination consistency
- Airflow/Control-M integration for automated triggering
- Advanced alerting beyond summary email
- Full reference data resolution from dataset registration service

### Risk Mitigation Strategy

**Technical Risks:**
- **Orchestrator complexity (highest risk):** Python CLI managing spark-submit invocations, failure isolation, rerun tracking, and DB writes. Mitigated by: building E2 as a dedicated epic, testing with mock spark-submit before real cluster integration.
- **Extensible path scanner:** Must support different HDFS path structures. Mitigated by: interface-based design, starting with one known path pattern, adding patterns incrementally.
- **DB schema rework is breaking:** Acceptable — project is in dev stage. Treat as fresh start for data layer.

**Adoption Risks:**
- **Dashboard adoption:** Data stewards may not check proactively at first. Mitigated by: summary email as a push notification that drives users to the dashboard. MCP as a pull channel where conversations already happen.
- **Single parent path validation:** Starting small is intentional — validates the full stack before scaling. Risk is that edge cases only appear at scale. Mitigated by: diverse test data fixtures covering format variations and legacy paths.

**Resource Risks:**
- **Single developer bottleneck:** Mitigated by BMad-driven epic execution with clear story boundaries. Deploy after E3 — if resources are constrained, Tier 2-4 can be deferred without impacting core value.
- **Minimum viable scope if squeezed:** If E3 proves too large, MCP could theoretically be deferred to a fast-follow. But it's preferred in MVP given its low incremental cost (wraps existing API).

## User Journeys

### Journey 1: Priya the Data Steward — "What happened overnight?"

**Situation:** Priya is responsible for data quality across the consumer banking LOB. Every morning she dreads opening her inbox — the only way she learns about data issues is when downstream teams complain, and by then the damage is done. She spends hours cross-referencing logs and emails to piece together what went wrong.

**Opening Scene:** Monday morning. Priya opens the DQS dashboard with her coffee. Instead of an empty void of uncertainty, she sees a summary view: 47 datasets monitored, 3 flagged with quality shifts over the weekend.

**Rising Action:** She clicks into her LOB. Two datasets show volume drops — one is a known weekend pattern (she dismisses it mentally), but the other dropped 80% unexpectedly. She drills down to the dataset level and sees the volume trend line — steady for 30 days, then a cliff on Saturday. Schema check passed, freshness is fine — so the pipeline ran, but something upstream reduced the records.

**Climax:** Priya catches this before the downstream analytics team runs their Monday morning reports. She flags the issue to the source system team with a screenshot of the trend. The source team confirms a config change broke a filter. They fix it before anyone downstream is impacted.

**Resolution:** Priya went from "I hope nothing broke" to "I can see exactly what changed and act before it matters." She checks the dashboard daily now. Complaints from her LOB's consumers have dropped to near-zero.

### Journey 2: Marcus the Executive — "Show me the big picture"

**Situation:** Marcus is VP of Enterprise Data. He reports to the CIO on data platform health quarterly. Today, he has no metrics — just anecdotes and escalation counts. When asked "how's our data quality?", the honest answer is "we don't really know."

**Opening Scene:** End of month. Marcus opens the DQS executive view. He sees a summary across all LOBs: overall DQS scores trending upward, two LOBs consistently underperforming.

**Rising Action:** He drills into the underperforming LOBs. One has persistent schema drift issues — source systems changing formats without notice. The other has chronic freshness gaps on weekends. He can see these aren't one-off incidents — they're patterns spanning weeks.

**Climax:** In his quarterly review, Marcus presents actual data: "Data quality improved 15% overall. Two LOBs need investment in source system discipline. Here are the specific check categories driving the issues." For the first time, he's making a data-driven argument about data quality.

**Resolution:** Marcus uses the monthly trend view to track improvement against the initiatives he funded. He finally has the metrics to justify platform investment and hold source system teams accountable.

### Journey 3: Sas and the Platform Ops Team — "Keep it running, fix what breaks"

**Situation:** The platform team manages DQS. They trigger runs, configure parent paths, manage check settings, and handle failures. The current pain isn't not knowing about failures — it's that diagnosing them is painful, involving log archaeology and manual DB queries.

**Opening Scene:** 6am ET. The overnight DQS run completed. Sas receives a summary email: 52 datasets processed, 49 passed, 2 failed (Spark errors), 1 had check failures. The email shows the orchestration run ID, start/end time, and a link to the dashboard.

**Rising Action:** Sas clicks through to the dashboard. The 2 Spark failures have error messages captured in the orchestration record — one is a Kerberos ticket expiry (infrastructure), the other is a corrupt Parquet file (data issue). He escalates the Kerberos issue to infra, and flags the corrupt file to the source team.

**Climax:** For the dataset with check failures, Sas doesn't need to dig through logs. The dashboard shows which checks failed, the metric values, and the historical trend. He decides to rerun the two Spark failures after the Kerberos ticket is renewed. The rerun is tracked with rerun_number=2 — audit trail preserved, previous metrics expired cleanly.

**Resolution:** What used to take an hour of log digging now takes 10 minutes of dashboard review. Config changes (adding new parent paths, adjusting check exclusions, tuning score weights) are done through the dataset_enrichment and check_config tables — no code redeployment needed.

### Journey 4: Alex the Downstream Consumer — "Is my data good today?"

**Situation:** Alex runs the fraud analytics pipeline. It depends on three specific datasets from the consumer zone. If the data is bad, the fraud models produce garbage results. Today, Alex runs the pipeline and hopes for the best — finding out hours later when results look wrong.

**Opening Scene:** 7am. Before kicking off the fraud pipeline, Alex opens the DQS dashboard and searches for the three datasets by name.

**Rising Action:** Two datasets show green — all checks passed, volume and freshness normal. The third shows a warning: freshness check detected data that's 6 hours staler than usual. Not a failure, but a deviation from the norm.

**Climax:** Alex decides to wait 30 minutes for the delayed data to land, then checks again. Freshness normalizes. Alex kicks off the fraud pipeline with confidence rather than hope.

**Resolution:** Alex no longer discovers bad data after wasting hours on a failed pipeline run. A 2-minute dashboard check before each run saves hours of rework. When data genuinely is bad, Alex knows before starting — not after.

### Journey 5: Deepa the Data Owner — "How is my data being received?"

**Situation:** Deepa manages the Omni source system that publishes transaction data to the consumer zone. She rarely hears feedback unless something breaks badly. She has no visibility into whether her data meets consumer expectations.

**Opening Scene:** Deepa checks DQS occasionally — maybe weekly or after a release. She searches for datasets matching her source system code.

**Rising Action:** She sees that her datasets consistently pass volume and freshness checks, but one dataset has been flagged for schema drift three times in the past month. Each time, her team added a new field to the Avro schema without realizing it triggered downstream detection.

**Climax:** Deepa realizes the schema changes are expected — they're rolling out new event attributes. But she now knows DQS is flagging them. She coordinates with the platform team to document the expected changes, avoiding false alarms.

**Resolution:** Deepa uses DQS as a feedback loop for her own releases. Before and after deployments, she checks whether her data quality scores changed. DQS becomes a quality gate she monitors proactively rather than hearing about reactively.

### Journey 6: LLM/MCP Consumer — "What failed last night?"

**Situation:** A team member is in a Slack-integrated chatbot or LLM tool and wants quick answers about data quality without opening the dashboard.

**Opening Scene:** "What failed last night?" typed into the chatbot.

**Rising Action:** The MCP tool queries the same API the dashboard uses. It returns: "3 datasets had check failures in last night's run. 2 volume anomalies in LOB Consumer Banking, 1 schema drift in LOB Markets."

**Climax:** "Show me trending for dataset ue90-omni-transactions over the last 30 days." The MCP tool returns a summary of DQS scores, flagged checks, and trend direction — all from the same drill-down API.

**Resolution:** Quick, natural language access to the same data the dashboard shows. No context-switching needed — quality answers arrive where the conversation is already happening.

### Journey Requirements Summary

| Journey | Key Capabilities Revealed |
|---------|--------------------------|
| Data Steward (Priya) | Dashboard summary view, LOB drill-down, dataset detail, trend visualization, check-level detail |
| Executive (Marcus) | LOB-level aggregation, monthly trending, DQS score tracking, cross-LOB comparison |
| Platform Ops (Sas) | Summary email, orchestration run tracking, error messages, rerun management, config tables (check_config, dataset_enrichment) |
| Downstream Consumer (Alex) | Dataset search by name, check status at a glance, freshness/volume indicators, quick pass/fail assessment |
| Data Owner (Deepa) | Source system filtering, schema drift history, before/after release comparison |
| LLM/MCP (Chatbot) | Natural language query → API → formatted response, same data as dashboard, trending summaries |

## Domain-Specific Requirements

### Compliance & Regulatory

- **No direct regulatory mandate.** DQS is an internal data quality observability tool, not a financial transaction system. It does not fall under SOX, OCC, or other financial regulatory frameworks directly.
- **Audit trail by convention.** The bank enforces soft-delete temporal patterns across all internal systems. DQS complies: all tables use `create_date`/`expiry_date`, no hard deletes, full rerun history preserved. This is an organizational standard, not a regulatory requirement.

### Data Sensitivity & Privacy

- **DQS reads sensitive data but does not surface it.** The Spark job reads actual HDFS data (Avro/Parquet) which may contain PII, PCI, or other sensitive fields. However, DQS only computes and stores aggregate metrics (counts, percentages, hashes, distributions) — raw data values are never persisted in Postgres or exposed through the API/dashboard.
- **eventAttribute explosion safety.** When recursively flattening JSON keys in eventAttribute, DQS extracts key names and computes distribution statistics over values — it does not store or display individual values. Key names (e.g., `paymentDetails.items[0].amount`) may appear in schema drift reports but actual field values do not.
- **No data export.** DQS does not export or replicate source data. All outputs are statistical summaries and check results.

### Operational Constraints

- **Kerberos authentication** required for HDFS/Spark access in production (keytab-based). Local dev uses username/password bypass.
- **No application-level authentication.** DQS is internal-only, secured by network/VPN boundary. No user auth, no RBAC.
- **90-day data retention.** Metrics and check results are soft-deleted after 90 days via expiry_date pattern. Retention purge is automated outside of DQS scope.
- **Eastern time zone** for all DQS operations. Source timestamps treated as UTC.
- **Deployment governance:** Internal platform tool with lightweight change management. No CAB approval required.

## Web Application Specific Requirements

### Project-Type Overview

DQS serve layer is a React single-page application backed by a FastAPI REST API. The dashboard provides drill-down reporting from summary → LOB → dataset → check detail. The MCP interface consumes the same API endpoints for LLM-powered natural language queries.

### Technical Architecture

**Frontend:**
- **Framework:** React (SPA)
- **Rendering:** Client-side rendering. No SSR needed (internal tool, no SEO).
- **State management:** Lightweight — data is read-only reporting, no complex form state or real-time collaboration.
- **Data fetching:** REST API calls on navigation and manual refresh. No WebSocket or real-time push.

**Backend:**
- **Framework:** FastAPI (Python)
- **API design:** Data-model-driven endpoints, not check-specific. Generic drill-down pattern: `/api/summary` → `/api/lobs/{lob}` → `/api/datasets/{id}` → `/api/datasets/{id}/metrics`
- **MCP layer:** FastMCP tools wrapping the same API endpoints

### Browser & Platform

- Modern browsers only: Chrome, Edge (latest versions). No legacy browser support.
- Desktop-first design. Tablet/mobile support not required (internal tool accessed from workstations).
- Internal network access only — no public internet exposure.

### Implementation

- **Deployment:** Docker-compose (FastAPI + React served together or separately)
- **No health checks or readiness probes** — simple deployment model
- **No authentication** — internal network boundary security
- **API-first design:** Dashboard and MCP are both consumers of the same REST API. No dashboard-specific backend logic.

## Functional Requirements

### Data Discovery & Ingestion

- FR1: System can scan configured parent HDFS paths and discover dataset subdirectories automatically
- FR2: System can extract dataset name (including lookup code) and partition date from HDFS path structures
- FR3: System can support different HDFS path structures via an extensible interface
- FR4: System can read Avro-format files from discovered dataset paths
- FR5: System can read Parquet-format files from discovered dataset paths
- FR6: System can resolve legacy dataset paths (e.g., `src_sys_nm=omni`) to lookup codes via the dataset_enrichment table
- FR7: System can process a specific date via parameter for backfill runs

### Quality Check Execution

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

### Orchestration & Run Management

- FR23: Platform ops can trigger an orchestration run manually via Python CLI
- FR24: System can invoke spark-submit for each discovered dataset within a parent path
- FR25: System can isolate failures so that one dataset failure does not halt the entire run
- FR26: System can track orchestration runs with a dq_orchestration_run record grouping all per-dataset dq_run records
- FR27: System can track rerun attempts with incrementing rerun_number per dataset
- FR28: System can expire previous run's metric calculations (via expiry_date) when a rerun is triggered
- FR29: System can capture error messages for failed datasets in the orchestration record
- FR30: System can record dataset counts per run (total, passed, failed)

### Data Model & Audit

- FR31: System can store all records with create_date and expiry_date columns (soft-delete temporal pattern)
- FR32: System can store numeric metrics (row counts, percentages, scores, stddev) in dq_metric_numeric table
- FR33: System can store structured/text results (schema diffs, duplicate values, change classifications) in dq_metric_detail table
- FR34: System can manage per-dataset check configuration via check_config table (dataset_pattern, check_type, enabled, explosion_level)
- FR35: System can manage per-dataset enrichment overrides via dataset_enrichment table (lookup code, custom weights, SLA hours, explosion level)

### Reporting Dashboard

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

### Reference Data Resolution

- FR46: System can resolve lookup codes to LOB, owner, and classification at query time in the serve layer
- FR47: System can cache reference data with twice-daily refresh
- FR48: System can return "N/A" for unresolved lookup codes (scaffold for future registration service integration)

### API & Integration

- FR49: External systems can access all dashboard capabilities via REST API with data-model-driven endpoints
- FR50: LLM consumers can query data quality information via MCP tools using natural language
- FR51: MCP tools can answer questions like "What failed last night?", "Show trending for dataset X", "Which LOB has worst quality?"
- FR52: System can send summary email to SRE team after each orchestration run containing: run ID, start/end time, dataset counts, pass/fail/error counts, top failures by check type, dashboard link

### Test Data

- FR53: Test data fixtures include 3-5 mock source systems covering Avro and Parquet formats with different path structures
- FR54: Test data includes mixed anomalies within otherwise healthy datasets (stale partitions, zero rows, schema drift, high nulls)
- FR55: Test data includes at least one legacy-format path (e.g., `src_sys_nm=omni`) to exercise lookup table resolution
- FR56: Test data eventAttribute values cover all JSON literal types: strings, numbers, booleans, arrays, objects, and nested combinations
- FR57: Historical baseline data for statistical checks is mocked directly in DB tables (single date on disk, mocked history in DB)

### Executive Reporting (Phase 3)

- FR58: Executive can view executive reporting suite with strategic dashboards (#26-30) (Phase 3)

## Non-Functional Requirements

### Performance

- **Dashboard response:** Summary view loads within 2 seconds. Drill-down navigation (LOB → dataset → check detail) responds within 1 second.
- **API response:** Standard queries < 500ms. Trend queries spanning 90 days < 2 seconds.
- **Concurrent users:** Dashboard supports dozens of simultaneous users (morning peak after overnight run completes).
- **Spark processing window:** Full orchestration run across all configured parent paths completes within 2-3 hours after partitioning job finishes (~3am ET). Results available in dashboard before business hours.
- **Batch DB write:** Single write per dataset after all checks complete. No per-check DB round trips during Spark execution.
- **Reference data cache:** Twice-daily refresh, query-time join for LOB/owner resolution.

### Security

- **Network boundary:** No application-level authentication. DQS is accessible only within internal network/VPN.
- **Sensitive data handling:** Spark reads raw HDFS data containing PII/PCI but only computes and stores aggregate metrics. No raw data values persisted in Postgres or exposed via API/dashboard/MCP.
- **Schema key names only:** eventAttribute explosion reports key names (e.g., `paymentDetails.items[0].amount`) in schema drift results. Actual field values are never stored or displayed.
- **Kerberos:** Production HDFS access uses keytab-based Kerberos authentication. Local dev bypasses with username/password.

### Scalability

- **Dataset coverage:** System scales from initial tens of datasets (one parent path) to hundreds of datasets (all parent paths) without architectural changes.
- **Data volume:** Designed for ~400 GB/day (stable). No requirement to scale beyond current volume.
- **Check extensibility:** Adding new check types (Tier 2-4) requires no changes to the serve layer, API, or dashboard — they consume metrics generically from dq_metric_numeric and dq_metric_detail tables.
- **90-day retention:** Metric data soft-deleted after 90 days. DB size grows linearly with dataset count, bounded by retention window.

### Integration

- **HDFS:** Read-only access via Spark on YARN. Avro and Parquet formats. Kerberos-authenticated in production.
- **Postgres:** Primary data store for all DQS state. Standard JDBC from Spark, SQLAlchemy from Python serve layer.
- **SMTP:** Summary email delivery to SRE distribution list after each orchestration run.
- **Dataset registration service (future):** Loosely coupled scaffold. Interface defined, returns N/A until implemented. Must not break if registration service is unavailable.
- **MCP:** FastMCP tools expose API endpoints for LLM consumption. Same data, different interface.

### Reliability

- **Failure isolation:** Single dataset failure must not halt orchestration run. All other datasets continue processing.
- **Audit integrity:** No data loss on failure. Partial runs recorded with error details. Reruns tracked with incrementing rerun_number. Previous metrics expired cleanly.
- **No HA requirement:** Single-instance deployment via docker-compose. Downtime during maintenance is acceptable.
- **Dashboard availability:** Best-effort during business hours. No uptime SLA. Stale data (from previous run) is acceptable if current run fails — users see last successful results.
