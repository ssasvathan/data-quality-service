# Epic List

## Epic 1: Project Foundation & Data Model
Platform team has a deployable stack with an auditable, temporal database schema and test data ready for development. Sets up all 4 component skeletons from Architecture starter templates, implements the complete Postgres schema (all tables, views, temporal pattern, constraints), and creates test data fixtures.
**FRs covered:** FR31, FR32, FR33, FR34, FR35, FR53, FR54, FR55, FR56, FR57

## Epic 2: Dataset Discovery & Tier 1 Quality Checks
System automatically discovers datasets on HDFS and computes quality metrics using all 5 Tier 1 checks, with results persisted to the database. Extensible HDFS path scanner, Avro/Parquet reading, legacy path resolution, all 5 Tier 1 checks (Freshness, Volume, Schema, Ops, DQS Score), configurable score weights, and atomic batch DB writes.
**FRs covered:** FR1, FR2, FR3, FR4, FR5, FR6, FR7, FR8, FR9, FR10, FR11, FR12, FR22

## Epic 3: Orchestration, Run Management & Notifications
Platform ops can trigger orchestration runs, handle failures with isolation, manage reruns with full audit trail, and receive summary email notifications. Python CLI orchestrator managing spark-submit invocations (one per parent path), two-layer failure isolation, rerun tracking with metric expiration, error capture, dataset counts, and SMTP summary email with dashboard link.
**FRs covered:** FR23, FR24, FR25, FR26, FR27, FR28, FR29, FR30, FR52

## Epic 4: Quality Dashboard & Drill-Down Reporting
Data stewards, executives, downstream consumers, and data owners can view, search, and investigate data quality through a 3-level drill-down web dashboard backed by a REST API. FastAPI REST API with data-model-driven endpoints, reference data resolution with LOB/owner cache, full React dashboard (Summary Card Grid -> LOB Stats+Table -> Dataset Master-Detail Split), dataset search, trend visualization, all UX components, and accessibility compliance.
**FRs covered:** FR36, FR37, FR38, FR39, FR40, FR41, FR42, FR43, FR44, FR45, FR46, FR47, FR48, FR49
**UX-DRs covered:** UX-DR1 through UX-DR23

## Epic 5: MCP & LLM Integration
LLM consumers can query data quality information via natural language MCP tools, getting the same data as the dashboard through conversational interfaces. FastMCP tools wrapping the same REST API endpoints.
**FRs covered:** FR50, FR51

## Epic 6: Tier 2 Quality Checks (Phase 2)
Deeper quality coverage with SLA monitoring, zero-row detection, breaking change protection, statistical distribution analysis, and timestamp validation. New check classes added to the existing DqCheck strategy interface.
**FRs covered:** FR13, FR14, FR15, FR16, FR17, FR18, FR19

## Epic 7: Tier 3 Intelligence & Executive Reporting (Phase 3)
Strategic quality intelligence with classification-weighted alerting, source system health correlation, and executive reporting suite for data-driven quality arguments.
**FRs covered:** FR20, FR21, FR58

---
