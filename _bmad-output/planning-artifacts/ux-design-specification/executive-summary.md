# Executive Summary

## Project Vision

DQS is a bank-wide data quality observability platform that transforms data quality management from reactive (complaint-driven) to proactive (automated detection). Processing ~400 GB/day across hundreds of HDFS datasets, it runs domain-agnostic statistical and structural checks via Apache Spark, stores results in Postgres, and surfaces insights through a drill-down reporting dashboard, REST API, and MCP-powered LLM interface. The UX must make statistical metadata meaningful to users who bring their own domain context — enabling the "Monday morning 5-minute confidence check" for data stewards and data-driven quality arguments for executives.

## Target Users

| Persona | Role | Primary Need | Usage Pattern |
|---------|------|-------------|---------------|
| Priya (Data Steward) | LOB data quality owner | Morning triage: what changed overnight? | Daily, drill-down from summary to check detail |
| Marcus (Executive) | VP Enterprise Data | Big-picture trends, cross-LOB comparison | Monthly/quarterly, stays at LOB-summary level |
| Sas (Platform Ops) | DQS operator | Failure diagnosis, rerun management, config | Daily, summary email + dashboard for failures |
| Alex (Downstream Consumer) | Pipeline operator | Quick pass/fail check before running pipelines | Ad-hoc, searches specific datasets by name |
| Deepa (Data Owner) | Source system manager | Monitor quality impact of releases | Weekly/post-release, filters by source system |
| LLM/MCP Consumer | Chatbot user | Natural language data quality queries | Ad-hoc, via Slack/chatbot integration |

## Key Design Challenges

1. **Multi-persona drill-down** — One dashboard serving 5 user types with different depth needs (executive glance vs. steward deep-dive vs. consumer quick-check). The drill-down hierarchy (Summary -> LOB -> Dataset -> Check Detail) must feel natural at every stop.
2. **Information density vs. clarity** — Hundreds of datasets, multiple check types, 90-day trends. The summary view must compress massive state into at-a-glance assessment without overwhelming. Benchmark: steward assesses weekend data health in under 5 minutes.
3. **Domain-agnostic presentation** — DQS has no business context for monitored data. Statistical/structural metrics must be presented in a way that's meaningful to users who bring their own domain knowledge.
4. **Extensibility without UI rework** — MVP ships with 5 Tier 1 checks. Tiers 2-4 add 15+ check types incrementally. The UI pattern must accommodate new check types without redesign, mirroring the data-model-driven API approach.

## Design Opportunities

1. **Traffic-light health scoring** — DQS score rolls up from check -> dataset -> LOB -> summary with color-coded health indicators, giving immediate signal at any drill depth.
2. **Trend-first storytelling** — Leading with trend direction (improving/degrading/stable) at each level lets users prioritize attention on what's changing, not just what's currently bad.
3. **Zero-click morning briefing** — Summary view designed so stewards can triage overnight quality without any navigation — everything needed is on the landing page.
