# Epic 7: Tier 3 Intelligence & Executive Reporting (Phase 3)

Strategic quality intelligence with classification-weighted alerting, source system health correlation, and executive reporting suite for data-driven quality arguments.

## Story 7.1: Classification-Weighted Alerting & Source System Health

As an **executive**,
I want quality alerts weighted by dataset classification (critical, standard, low) and source system health aggregation,
So that alerting prioritizes the most impactful datasets and source system patterns are visible.

**Acceptance Criteria:**

**Given** datasets with classification levels from `dataset_enrichment`
**When** the Classification-Weighted Alerting check (#51) executes
**Then** it computes classification-adjusted severity scores and writes metrics distinguishing critical dataset failures from low-priority ones

**Given** multiple datasets from the same source system
**When** the Source System Health check (#52) executes
**Then** it aggregates quality scores by source system and identifies source systems with consistently poor quality across their datasets
**And** results are written to `dq_metric_numeric` and `dq_metric_detail` for generic consumption

## Story 7.2: Correlation & Inferred SLA Checks

As a **data steward**,
I want correlation analysis across related datasets and inferred SLA detection for datasets without explicit SLA configuration,
So that cross-dataset quality patterns and implicit delivery expectations are surfaced.

**Acceptance Criteria:**

**Given** datasets that share a source system or LOB
**When** the Correlation check (#55) executes
**Then** it identifies correlated quality shifts (e.g., multiple datasets from the same source degrading simultaneously) and writes correlation metrics

**Given** a dataset with 30+ days of historical freshness data but no configured SLA
**When** the Inferred SLA check (#57) executes
**Then** it computes an inferred delivery window based on historical arrival patterns and flags deviations from the inferred pattern

## Story 7.3: Lineage, Orphan Detection & Cross-Destination Consistency

As a **platform operator**,
I want lineage checks, orphan detection, and cross-destination consistency validation,
So that data flow integrity is verified beyond individual dataset quality.

**Acceptance Criteria:**

**Given** datasets with known lineage relationships
**When** the Lineage check (#11) executes
**Then** it validates that upstream datasets are healthy before flagging downstream issues

**Given** dataset directories on HDFS
**When** the Orphan Detection check (#12) executes
**Then** it identifies datasets that exist on HDFS but are not tracked in DQS (potentially abandoned or misconfigured)

**Given** datasets replicated to multiple destinations
**When** the Cross-Destination Consistency check (#53) executes
**Then** it compares row counts, schema hashes, and freshness across destinations and flags inconsistencies

## Story 7.4: Executive Reporting Suite

As an **executive**,
I want strategic dashboards (#26-30) providing executive-level quality reporting,
So that I can make data-driven arguments about quality investment and hold teams accountable.

**Acceptance Criteria:**

**Given** the executive reporting suite is implemented
**When** an executive accesses the reporting views
**Then** they can view cross-LOB quality scorecards with monthly/quarterly trends
**And** they can view source system accountability reports showing quality by source team
**And** they can view improvement tracking against baseline metrics
**And** all reports use the existing API pattern and DQS Score as the primary metric

**Given** the existing Summary view already shows LOB scores
**When** executive reports are accessed
**Then** they add deeper aggregation (monthly rollups, quarterly comparisons, YoY trends) on top of the existing data model
