### Topic: 
Metadata-level data quality checks for a bank's real-time streaming data platform (S3-backed, DBT-powered Datahub) that must remain domain-agnostic while serving various lines of business

#### Goals:

Proactive issue detection before downstream impact
Executive-level data quality dashboards/reporting
Enterprise data quality metrics that scale without requiring data domain expertise
Key Constraints

## Must operate at metadata level only (no data content inspection)
- Platform-agnostic approach across multiple LOBs
- Builds on existing dataset registration service (schema, owners, destinations, governance)
- Goes beyond data governance into quality hygiene
- Bank context = regulatory/compliance considerations likely relevant

###Session Setup

## Approach Selected: AI-Recommended Techniques - customized technique suggestions based on session goals

## Initial Seed Ideas:

- Day-over-day variance trending
- Industry standard research for large-scale warehouse quality patterns

### Technique Selection

**Approach:** AI-Recommended Techniques
**Analysis Context:** Metadata-level data quality checks with focus on proactive detection, executive reporting, platform-agnostic design

## Recommended Technique Sequence:

- Cross-Pollination (20-25 min) - Transfer solutions from adjacent industries
- Morphological Analysis (25-30 min) - Systematic metadata dimension mapping

### Ideation Session

### Technique 1: Cross-Pollination (Complete)

Focus: Transfer proven metadata quality patterns from adjacent industries

## Freshness Checks

[Freshness #1]: Heartbeat Monitor Concept: Track "last modified timestamp" per dataset and compare against expected cadence. Alert when data is "stale" beyond threshold. Novelty: Catches missing data in the quiet period before dashboards break.

[Freshness #2]: SLA Clock with Countdown Alerts Concept: Tiered alerts based on expected delivery windows: "2 hours until breach," "30 minutes," "BREACHED." Novelty: Transforms SLA from binary pass/fail into proactive risk gradient.

## Volume Checks

[Volume #3]: Statistical Baseline + Variance Bands Concept: Rolling 30-day average row count. Flag when today's volume falls outside ±2 standard deviations. Novelty: Catches "upstream stopped sending data type" without domain knowledge.

[Volume #4]: Zero-Row Detection with Grace Period Concept: Track historical zero-day patterns. Alert on unexpected zeros while preventing false alarms on weekends/holidays. Novelty: Distinguishes "legitimately empty" from "something broke."

## Schema Checks

[Schema #5]: Column Fingerprint Drift Concept: Hash schema definition daily. Any change = automatic notification to registered downstream consumers. Novelty: Pure structural detection—no domain knowledge needed.

[Schema #6]: Breaking vs Non-Breaking Change Classification Concept: Categorize changes: Column added (non-breaking), removed/renamed/type-changed (breaking). Different alert severity. Novelty: Reduces alert fatigue by distinguishing "heads up" from "stop everything."

## Distribution Checks

[Distribution #7]: Null Percentage Drift Concept: Track null % per column over time. Alert when ratio shifts significantly. Novelty: Catches "upstream started sending blanks" without understanding business logic.

[Distribution #8]: Cardinality Anomaly Detection Concept: Monitor distinct value count per column. Alert on dramatic increases or decreases. Novelty: Detects data collapse (filter broke) and data explosion (duplicate keys).

[Distribution #9]: Column Completeness Trending Concept: Track % of non-null, non-empty values per column over time. Novelty: Executive-friendly: "Email completeness dropped from 98% to 72%."

[Distribution #10]: Value Length Distribution Concept: Track average/min/max string length. Sudden changes indicate format changes. Novelty: Catches format drift without parsing content.

## Lineage Checks

[Lineage #11]: Upstream Health Propagation Concept: When Dataset A fails, automatically flag downstream B and C as "potentially impacted." Novelty: Proactive blast radius: "12 downstream datasets at risk."

[Lineage #12]: Orphan Dataset Detection Concept: Identify datasets with no upstream lineage AND no downstream consumers. Novelty: Data hygiene: find abandoned datasets costing storage.

## Exchange Pattern Checks

[Exchange #15]: Sequence Gap Detection Concept: Detect gaps in natural sequence IDs or timestamps. Novelty: Works for any ordered data without knowing what it represents.

[Exchange #16]: Duplicate Primary Key Alert Concept: Daily uniqueness check on registered primary/natural keys. Novelty: Simple but catches issues before joins fail downstream.

[Exchange #18]: Timestamp Sanity Bounds Concept: Flag timestamps in future or before reasonable past (e.g., pre-2000). Novelty: Catches timezone bugs, epoch conversion errors universally.

## Governance Checks

[Governance #20]: Access Pattern Anomaly Concept: Track who/what queries each dataset. Alert on dramatic access pattern shifts. Novelty: Security + quality intersection.

## Operations Checks

[Operations #22]: Time in Transit Monitoring Concept: Track data transit time from source to Datahub. Alert when exceeds historical norms. Novelty: Catches pipeline bottlenecks before SLA breach.

[Operations #23]: Batch Size Variance Concept: Track incoming batch sizes. Alert on unusually large (backfill flood) or small (partial failure). Novelty: Operational health independent of data content.

[Operations #24]: Source System Heartbeat Concept: Track "last successful delivery" per source. Alert on unusual silence. Novelty: Platform team can proactively reach out to LOB.

## Executive Reporting

[Executive #25]: Data Quality Score (DQS) Concept: Composite score per dataset: weighted average of freshness, completeness, schema stability, volume consistency. Single 0-100 number. Novelty: Executive-friendly: "Your data estate is 87% healthy."

[Executive #26]: Quality Trending by LOB Concept: Aggregate DQS by line of business. "Retail Banking: 92%, Wealth Management: 78%." Novelty: Enables executive conversations about LOB performance.

[Executive #27]: Incident Heatmap Concept: Calendar view showing quality incidents over time. Novelty: Pattern recognition—clusters reveal systemic issues.

[Executive #28]: Mean Time to Detection (MTTD) Concept: Track how long quality issues exist before detection. Novelty: Meta-metric about quality system effectiveness.

[Executive #29]: Blast Radius Index Concept: For each incident, calculate downstream datasets/consumers affected. Novelty: Quantifies "small incident" vs "enterprise-wide impact."

[Executive #30]: Data Debt Inventory Concept: Track datasets with recurring quality issues. "Top 10 problematic datasets." Novelty: Turns reactive firefighting into strategic remediation.

### Technique 2: Morphological Analysis

Focus: Map check types against available metadata dimensions

## New Ideas from Matrix Combinations

[Matrix #51]: Classification-Weighted Alerting Concept: Alert severity based on classification tier. Restricted = immediate, Internal = daily batch. Novelty: Not all data deserves equal urgency.

[Matrix #52]: Source System Health Score Concept: Aggregate quality metrics by source system ID. Novelty: Identify problematic upstream systems without knowing data content.

[Matrix #53]: Cross-Destination Consistency Check Concept: Compare metrics for same dataset across multiple allowed Datahubs. Novelty: Catches replication/sync issues.

[Matrix #54]: Owner Accountability Report Concept: Weekly report per owner: datasets, incidents, average DQS. Novelty: Pushes quality ownership to LOBs.

[Matrix #55]: Source System Correlation Concept: Cluster simultaneous failures from same source as single incident. Novelty: Reduces alert noise.

[Matrix #56]: Classification-Tiered Monitoring Depth Concept: Restricted = all 25 checks, Internal = basic 5 checks. Novelty: Right-size monitoring investment.

[Matrix #57]: Inferred SLA from Statistical Baseline Concept: Auto-generate expected arrival windows from history. Novelty: Platform-derived SLA without LOB commitment.

[Matrix #58]: New Source System Onboarding Alert Concept: Flag previously unseen source system IDs. Novelty: Catches "where did this come from?" issues.

[Matrix #59]: Owner-Grouped Blast Radius Concept: Show incident impact grouped by owner. Novelty: Executive view of team impacts.


### Recommended Implementation Tiers

## Tier 1: Foundation (Quick Wins)

- Freshness #1: Heartbeat Monitor
- Volume #3: Statistical Baseline + Variance
- Schema #5: Column Fingerprint Drift
- Operations #24: Source System Heartbeat
- Executive #25: Data Quality Score (DQS)
- Executive Reporting UI
- MCP Service

## Tier 2: Core Detection

- Freshness #2: SLA Countdown Alerts
- Volume #4: Zero-Row Detection
- Schema #6: Breaking Change Classification
- Distribution #7-10: Null %, Cardinality, Completeness, Value Length
- Exchange #16: Duplicate Primary Key
- Exchange #18: Timestamp Sanity

## Tier 3: Intelligence Layer

- Matrix #51: Classification-Weighted Alerting
- Matrix #52: Source System Health Score
- Matrix #55: Source System Correlation
- Matrix #57: Inferred SLA from Baseline
- Executive #26-30: LOB Trending, Heatmap, MTTD, Blast Radius, Data Debt

##Tier 4: Advanced (Requires Lineage)

- Lineage #11: Upstream Health Propagation
- Lineage #12: Orphan Dataset Detection
- Matrix #53: Cross-Destination Consistenc